package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectRoomControllerDockerIT {
    private static final String MINIO_ACCESS_KEY = "aegisflow";
    private static final String MINIO_SECRET_KEY = "aegisflow-secret";
    private static final String BUCKET = "aegisflow-project-room-it";

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
    private ProjectService projectService;

    @Autowired
    private ReviewService reviewService;

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
    void projectRoomDrawerActionsPersistToPostgresAndMinio() throws Exception {
        var project = projectService.submitBrd(
                "Docker Project Room",
                "Payments Business Team",
                "Payments",
                document("project-room-brd-v1.md", "Initial BRD")
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();

        MockMultipartFile evidence = new MockMultipartFile(
                "file",
                "issuer-timeout-analysis.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "issuer timeout evidence".getBytes(StandardCharsets.UTF_8)
        );
        String attachmentResponse = mockMvc.perform(multipart(
                                "/api/project-rooms/{projectId}/reviews/{reviewId}/attachments",
                                project.projectId(),
                                review.reviewId()
                        )
                        .file(evidence)
                        .param("actor", "architect-docker")
                        .param("description", "Issuer timeout reference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(review.reviewId().toString()))
                .andExpect(jsonPath("$.storageUri", startsWith("aegisflow-storage://")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode attachmentJson = objectMapper.readTree(attachmentResponse);
        String attachmentStorageUri = attachmentJson.get("storageUri").asText();
        assertReviewAttachmentWasPersisted(review.reviewId(), "issuer-timeout-analysis.pdf", attachmentStorageUri);
        assertMinioObject("projects/%s/reviews/%s/attachments/issuer-timeout-analysis.pdf".formatted(project.projectId(), review.reviewId()), "issuer timeout evidence");

        String decisionResponse = mockMvc.perform(post("/api/project-rooms/{projectId}/technical-review/decision", project.projectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE_WITH_CHANGE",
                                  "actor": "architect-docker",
                                  "correction": "Reuse the payment orchestration capability and add issuer timeout handling."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE_WITH_CHANGE"))
                .andExpect(jsonPath("$.nextState").value("ESTIMATION"))
                .andExpect(jsonPath("$.generatedArtifactId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode decisionJson = objectMapper.readTree(decisionResponse);
        UUID decisionId = UUID.fromString(decisionJson.get("decisionId").asText());
        UUID generatedArtifactId = UUID.fromString(decisionJson.get("generatedArtifactId").asText());
        assertReviewDecisionWasPersisted(decisionId, review.reviewId(), "APPROVE_WITH_CHANGE", "ESTIMATION");
        assertProjectState(project.projectId(), WorkflowState.ESTIMATION);
        assertArtifactWasPersisted(project.projectId(), generatedArtifactId, "ARCHITECTURE_ANALYSIS", 1);

        var systemAnalysis = projectService.addGeneratedArtifact(
                project.projectId(),
                ArtifactType.SYSTEM_ANALYSIS,
                "system-analysis-v1.md",
                MediaType.TEXT_MARKDOWN_VALUE,
                "System flow detail".getBytes(StandardCharsets.UTF_8)
        ).orElseThrow();

        mockMvc.perform(get(
                        "/api/project-rooms/{projectId}/artifacts/{artifactId}/versions/{version}",
                        project.projectId(),
                        systemAnalysis.artifactId(),
                        systemAnalysis.version()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifact.artifactType").value("SYSTEM_ANALYSIS"))
                .andExpect(jsonPath("$.downloadable").value(true))
                .andExpect(jsonPath("$.downloadPath").exists());

        mockMvc.perform(get(
                        "/api/project-rooms/{projectId}/artifacts/{artifactId}/versions/{version}/content",
                        project.projectId(),
                        systemAnalysis.artifactId(),
                        systemAnalysis.version()
                ))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"system-analysis-v1.md\""))
                .andExpect(content().string("System flow detail"));

        MockMultipartFile revisedBrd = new MockMultipartFile(
                "brdFile",
                "project-room-brd-v2.md",
                MediaType.TEXT_MARKDOWN_VALUE,
                "Revised BRD".getBytes(StandardCharsets.UTF_8)
        );
        String brdResponse = mockMvc.perform(multipart("/api/project-rooms/{projectId}/brd-versions", project.projectId())
                        .file(revisedBrd))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactType").value("BRD"))
                .andExpect(jsonPath("$.version").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID brdArtifactId = UUID.fromString(objectMapper.readTree(brdResponse).get("artifactId").asText());
        assertProjectState(project.projectId(), WorkflowState.BRD_SUBMITTED);
        assertArtifactWasPersisted(project.projectId(), brdArtifactId, "BRD", 2);
        assertMinioObject("projects/%s/artifacts/%s/versions/2/project-room-brd-v2.md".formatted(project.projectId(), brdArtifactId), "Revised BRD");

        mockMvc.perform(get("/api/project-rooms/{projectId}/activity", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItem("PROJECT_CREATED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("ARTIFACT_VERSION_CREATED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("HUMAN_REVIEW_DECIDED")));
    }

    private SubmittedDocument document(String fileName, String content) {
        return new SubmittedDocument(
                fileName,
                MediaType.TEXT_MARKDOWN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void assertReviewAttachmentWasPersisted(UUID reviewId, String fileName, String storageUri) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM human_review_attachments
                        WHERE review_id = ?
                          AND file_name = ?
                          AND storage_uri = ?
                        """,
                Integer.class,
                reviewId,
                fileName,
                storageUri
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertReviewDecisionWasPersisted(UUID decisionId, UUID reviewId, String decision, String nextState) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM human_review_decisions
                        WHERE decision_id = ?
                          AND review_id = ?
                          AND decision = ?
                          AND next_state = ?
                        """,
                Integer.class,
                decisionId,
                reviewId,
                decision,
                nextState
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertProjectState(UUID projectId, WorkflowState expectedState) {
        String currentState = jdbcTemplate.queryForObject(
                "SELECT current_state FROM projects WHERE project_id = ?",
                String.class,
                projectId
        );
        assertThat(currentState).isEqualTo(expectedState.name());
    }

    private void assertArtifactWasPersisted(UUID projectId, UUID artifactId, String artifactType, int version) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM artifact_versions
                        WHERE project_id = ?
                          AND artifact_id = ?
                          AND artifact_type = ?
                          AND version = ?
                          AND storage_bucket = ?
                        """,
                Integer.class,
                projectId,
                artifactId,
                artifactType,
                version,
                BUCKET
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertMinioObject(String objectKey, String expectedContent) throws Exception {
        minioClient.statObject(
                StatObjectArgs.builder()
                        .bucket(BUCKET)
                        .object(objectKey)
                        .build()
        );
        try (var response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(BUCKET)
                        .object(objectKey)
                        .build()
        )) {
            assertThat(new String(response.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(expectedContent);
        }
    }
}
