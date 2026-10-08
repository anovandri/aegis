package com.aegisflow.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeAdapterDescriptor;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeConnectionCheckResult;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceAdapter;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceConnection;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourcePayload;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceResource;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class KnowledgeControllerDockerIT {
    private static final String MINIO_ACCESS_KEY = "aegisflow";
    private static final String MINIO_SECRET_KEY = "aegisflow-secret";
    private static final String BUCKET = "aegisflow-docker-it";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("aegisflow")
            .withUsername("aegisflow")
            .withPassword("aegisflow-secret");

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("bitnamilegacy/minio:latest")
    )
            .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void dockerBackedProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/vendor/postgresql");
        registry.add("aegisflow.storage.provider", () -> "minio");
        registry.add("aegisflow.storage.bucket", () -> BUCKET);
        registry.add("aegisflow.storage.endpoint", () -> "http://%s:%d".formatted(MINIO.getHost(), MINIO.getMappedPort(9000)));
        registry.add("aegisflow.storage.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("aegisflow.storage.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("aegisflow.temporal.enabled", () -> "false");
        registry.add("aegisflow.llm.provider", () -> "local");
        registry.add("aegisflow.knowledge.embedding.provider", () -> "local");
    }

    @Test
    void storesKnowledgeDocumentInPostgresPgvectorAndMinio() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "docker-payment-standard.md",
                "text/markdown",
                """
                        # Docker Payment Standard
                        QRIS payment requirements must include SLA, timeout handling, retry behavior, reversal, and reconciliation.
                        """.getBytes(StandardCharsets.UTF_8)
        );

        String response = mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(file)
                        .param("sourceTitle", "Docker Payment Standard")
                        .param("sourceId", "docker-payment-standard")
                        .param("sourceType", "REQUIREMENT_STANDARD")
                        .param("authority", "AUTHORITATIVE")
                        .param("allowedAgents", "Requirement Analyst Agent")
                        .param("workflowStates", "REQUIREMENT_ANALYSIS")
                        .param("tags", "docker,payment,qris"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceId").value("docker-payment-standard"))
                .andExpect(jsonPath("$.versions[0].storageBucket").value(BUCKET))
                .andExpect(jsonPath("$.versions[0].extractedText", containsString("QRIS payment requirements")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        UUID documentId = UUID.fromString(json.get("documentId").asText());

        assertPostgresDocumentAndChunks(documentId);
        assertPgvectorMigrationAndChunkVector(documentId);
        assertMinioObjectWasStored(documentId, "Docker Payment Standard", "reversal", "reconciliation");

        mockMvc.perform(get("/api/knowledge/search")
                .param("q", "QRIS reversal reconciliation SLA")
                .param("agentName", "Requirement Analyst Agent")
                .param("workflowState", "REQUIREMENT_ANALYSIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sourceTitle", hasItem("Docker Payment Standard")));
    }

    @Test
    void connectorDiscoverySyncRunsAndAuditArePersistedWithDockerBackedDependencies() throws Exception {
        mockMvc.perform(post("/api/knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "docker-sync-source",
                                  "name": "Docker Sync Source",
                                  "sourceType": "PREVIOUS_PROJECT",
                                  "authority": "SUPPORTING",
                                  "ownerTeam": "Architecture",
                                  "freshnessSlaHours": 24,
                                  "syncMode": "MANUAL_PULL",
                                  "sensitivityPolicy": "INTERNAL",
                                  "allowedAgents": ["Requirement Analyst Agent", "Architecture Agent"],
                                  "workflowStates": ["REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS"],
                                  "tags": ["docker-sync", "adapter"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceId").value("docker-sync-source"))
                .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"));

        mockMvc.perform(get("/api/knowledge/sources").param("status", "NEEDS_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sourceId", hasItem("docker-sync-source")));

        mockMvc.perform(get("/api/knowledge/adapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].adapterType", hasItem("DOCKER_SYNC")));

        String connectionResponse = mockMvc.perform(post("/api/knowledge/sources/{sourceId}/connections", "docker-sync-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adapterType": "DOCKER_SYNC",
                                  "connectionName": "Docker deterministic upstream",
                                  "resourceLocator": "docker-upstream",
                                  "authType": "NONE",
                                  "configJson": "{}"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.connectionStatus").value("CONFIGURED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID connectionId = UUID.fromString(objectMapper.readTree(connectionResponse).get("connectionId").asText());

        mockMvc.perform(get("/api/knowledge/sources/{sourceId}/connections", "docker-sync-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].connectionId").value(connectionId.toString()));

        mockMvc.perform(get("/api/knowledge/connections/{connectionId}", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adapterType").value("DOCKER_SYNC"));

        mockMvc.perform(post("/api/knowledge/connections/{connectionId}/check", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"));

        String discoverResponse = mockMvc.perform(post("/api/knowledge/connections/{connectionId}/discover", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].externalId", hasItem("docker-resource-architecture")))
                .andExpect(jsonPath("$[*].externalId", hasItem("docker-resource-requirements")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID firstResourceId = UUID.fromString(objectMapper.readTree(discoverResponse).get(0).get("resourceId").asText());

        mockMvc.perform(get("/api/knowledge/connections/{connectionId}/resources", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        String contentResponse = mockMvc.perform(get("/api/knowledge/resources/{resourceId}/content", firstResourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("docker-architecture.md"))
                .andExpect(jsonPath("$.mediaType").value("text/markdown"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String content = new String(Base64.getDecoder().decode(objectMapper.readTree(contentResponse).get("contentBase64").asText()), StandardCharsets.UTF_8);
        assertThat(content).contains("Docker architecture knowledge", "event-driven");

        mockMvc.perform(post("/api/knowledge/sources/{sourceId}/sync", "docker-sync-source"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsChanged").value(2));

        mockMvc.perform(post("/api/knowledge/sources/{sourceId}/sync", "docker-sync-source"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsChanged").value(0));

        mockMvc.perform(get("/api/knowledge/sources/{sourceId}/sync-runs", "docker-sync-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].recordsChanged", hasItem(2)))
                .andExpect(jsonPath("$[*].recordsChanged", hasItem(0)));

        mockMvc.perform(get("/api/knowledge/sources/{sourceId}", "docker-sync-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FRESH"))
                .andExpect(jsonPath("$.documentCount").value(2))
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/knowledge/sources/{sourceId}/preview", "docker-sync-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.sourceId").value("docker-sync-source"))
                .andExpect(jsonPath("$.metrics[*].label", hasItem("Documents")))
                .andExpect(jsonPath("$.metrics[*].label", hasItem("Resources")))
                .andExpect(jsonPath("$.eligibility.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.connections", hasSize(1)))
                .andExpect(jsonPath("$.resources", hasSize(2)))
                .andExpect(jsonPath("$.recentSyncRuns", hasSize(2)))
                .andExpect(jsonPath("$.evidence[*].sourceTitle", hasItem("Docker Architecture Notes")));

        mockMvc.perform(get("/api/knowledge/audit").param("sourceId", "docker-sync-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[*].action", hasItem("SOURCE_SYNC_SUCCEEDED")));

        mockMvc.perform(get("/api/knowledge/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSources").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(2)));

        mockMvc.perform(get("/api/knowledge/search")
                        .param("q", "event-driven reconciliation timeout")
                        .param("agentName", "Architecture Agent")
                        .param("workflowState", "ARCHITECTURE_ANALYSIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sourceTitle", hasItem("Docker Architecture Notes")));

        assertSyncedSourceWasPersisted(connectionId);
    }

    private void assertPostgresDocumentAndChunks(UUID documentId) {
        Integer documentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_documents WHERE document_id = ? AND source_id = ?",
                Integer.class,
                documentId,
                "docker-payment-standard"
        );
        assertThat(documentCount).isEqualTo(1);

        Integer versionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document_versions WHERE document_id = ? AND version = 1 AND extracted_text LIKE ?",
                Integer.class,
                documentId,
                "%reconciliation%"
        );
        assertThat(versionCount).isEqualTo(1);

        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunks WHERE document_id = ? AND document_version = 1",
                Integer.class,
                documentId
        );
        assertThat(chunkCount).isGreaterThanOrEqualTo(1);
    }

    private void assertPgvectorMigrationAndChunkVector(UUID documentId) {
        Integer extensionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                Integer.class
        );
        assertThat(extensionCount).isEqualTo(1);

        Integer vectorColumnCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_name = 'knowledge_chunks'
                          AND column_name = 'embedding_vector'
                        """,
                Integer.class
        );
        assertThat(vectorColumnCount).isEqualTo(1);

        Integer populatedVectorCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunks WHERE document_id = ? AND embedding_vector IS NOT NULL",
                Integer.class,
                documentId
        );
        assertThat(populatedVectorCount).isGreaterThanOrEqualTo(1);
    }

    private void assertMinioObjectWasStored(UUID documentId, String... expectedContentFragments) throws Exception {
        Map<String, Object> version = jdbcTemplate.queryForMap(
                """
                        SELECT storage_bucket, storage_object_key, storage_version_id
                        FROM knowledge_document_versions
                        WHERE document_id = ?
                          AND version = 1
                        """,
                documentId
        );
        String bucket = version.get("storage_bucket").toString();
        String objectKey = version.get("storage_object_key").toString();
        String storageVersionId = version.get("storage_version_id") == null ? null : version.get("storage_version_id").toString();

        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucket)
                .build());
        assertThat(bucketExists).isTrue();

        minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .versionId(storageVersionId)
                .build());

        byte[] storedBytes;
        try (var object = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .versionId(storageVersionId)
                .build())) {
            storedBytes = object.readAllBytes();
        }
        assertThat(new String(storedBytes, StandardCharsets.UTF_8))
                .contains(expectedContentFragments);
        assertThat(storageVersionId).isNotBlank();
    }

    private void assertSyncedSourceWasPersisted(UUID connectionId) throws Exception {
        Integer connectionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_source_connections WHERE connection_id = ? AND connection_status = 'CONNECTED'",
                Integer.class,
                connectionId
        );
        assertThat(connectionCount).isEqualTo(1);

        Integer resourceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_source_resources WHERE connection_id = ? AND status = 'DISCOVERED'",
                Integer.class,
                connectionId
        );
        assertThat(resourceCount).isEqualTo(2);

        Integer syncRunCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_sync_runs WHERE source_id = ? AND status = 'SUCCEEDED'",
                Integer.class,
                "docker-sync-source"
        );
        assertThat(syncRunCount).isEqualTo(2);

        Integer syncedResourceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_synced_resource_documents WHERE source_id = ?",
                Integer.class,
                "docker-sync-source"
        );
        assertThat(syncedResourceCount).isEqualTo(2);

        List<UUID> documentIds = jdbcTemplate.queryForList(
                """
                        SELECT kd.document_id
                        FROM knowledge_documents kd
                        JOIN knowledge_synced_resource_documents ksrd
                            ON ksrd.document_id = kd.document_id
                        WHERE ksrd.source_id = ?
                        """,
                UUID.class,
                "docker-sync-source"
        );
        assertThat(documentIds).hasSize(2);

        for (UUID documentId : documentIds) {
            assertPostgresDocumentAndChunksForSyncedSource(documentId, "docker-sync-source");
            assertPgvectorMigrationAndChunkVector(documentId);
            assertMinioObjectWasStored(documentId, "Docker");
        }
    }

    private void assertPostgresDocumentAndChunksForSyncedSource(UUID documentId, String sourceId) {
        Integer documentCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM knowledge_documents kd
                        JOIN knowledge_synced_resource_documents ksrd
                            ON ksrd.document_id = kd.document_id
                        WHERE kd.document_id = ?
                          AND ksrd.source_id = ?
                        """,
                Integer.class,
                documentId,
                sourceId
        );
        assertThat(documentCount).isEqualTo(1);

        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunks WHERE document_id = ? AND document_version = 1",
                Integer.class,
                documentId
        );
        assertThat(chunkCount).isGreaterThanOrEqualTo(1);
    }

    @TestConfiguration
    static class DockerSyncAdapterConfiguration {
        @Bean
        KnowledgeSourceAdapter dockerSyncKnowledgeSourceAdapter() {
            return new KnowledgeSourceAdapter() {
                @Override
                public KnowledgeAdapterDescriptor descriptor() {
                    return new KnowledgeAdapterDescriptor(
                            "DOCKER_SYNC",
                            "Docker Sync",
                            "Deterministic adapter used by Docker-backed integration tests.",
                            List.of("NONE"),
                            List.of("DOCUMENT"),
                            List.of(),
                            true
                    );
                }

                @Override
                public KnowledgeConnectionCheckResult checkConnection(KnowledgeSourceConnection connection, Instant checkedAt) {
                    return new KnowledgeConnectionCheckResult("CONNECTED", "Docker test adapter connected", checkedAt);
                }

                @Override
                public List<KnowledgeSourceResource> discoverResources(KnowledgeSourceConnection connection, Instant discoveredAt) {
                    return List.of(
                            new KnowledgeSourceResource(
                                    UUID.randomUUID(),
                                    connection.connectionId(),
                                    "docker-resource-architecture",
                                    "DOCUMENT",
                                    "Docker Architecture Notes",
                                    "docker://architecture",
                                    "v1",
                                    "hash-architecture-v1",
                                    "DISCOVERED",
                                    discoveredAt
                            ),
                            new KnowledgeSourceResource(
                                    UUID.randomUUID(),
                                    connection.connectionId(),
                                    "docker-resource-requirements",
                                    "DOCUMENT",
                                    "Docker Requirement Notes",
                                    "docker://requirements",
                                    "v1",
                                    "hash-requirements-v1",
                                    "DISCOVERED",
                                    discoveredAt
                            )
                    );
                }

                @Override
                public Optional<KnowledgeSourcePayload> fetchResource(KnowledgeSourceConnection connection, KnowledgeSourceResource resource) {
                    if (resource.externalId().equals("docker-resource-architecture")) {
                        return Optional.of(new KnowledgeSourcePayload(
                                "docker-architecture.md",
                                "text/markdown",
                                "Docker architecture knowledge covers event-driven integration, ownership, and observability.".getBytes(StandardCharsets.UTF_8),
                                "v1"
                        ));
                    }
                    if (resource.externalId().equals("docker-resource-requirements")) {
                        return Optional.of(new KnowledgeSourcePayload(
                                "docker-requirements.md",
                                "text/markdown",
                                "Docker requirement knowledge covers timeout handling, retry behavior, reversal, and reconciliation.".getBytes(StandardCharsets.UTF_8),
                                "v1"
                        ));
                    }
                    return Optional.empty();
                }
            };
        }
    }
}
