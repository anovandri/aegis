package com.aegisflow.api.web;

import com.aegisflow.api.infrastructure.knowledge.KnowledgeAdapterDescriptor;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeConnectionCheckResult;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceAdapter;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceConnection;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourcePayload;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KnowledgeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createKnowledgeDocumentStoresInitialVersion() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qris-lessons.md",
                MediaType.TEXT_PLAIN_VALUE,
                "QRIS projects require expiry, reversal, and reconciliation requirements.".getBytes()
        );

        mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(file)
                        .param("sourceTitle", "QRIS Lessons")
                        .param("sourceId", "qris-lessons")
                        .param("sourceType", "PREVIOUS_PROJECT")
                        .param("authority", "SUPPORTING")
                        .param("allowedAgents", "Requirement Analyst Agent")
                        .param("workflowStates", "REQUIREMENT_ANALYSIS")
                        .param("tags", "qris,payment,reversal,reconciliation"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceId").value("qris-lessons"))
                .andExpect(jsonPath("$.latestVersion").value(1))
                .andExpect(jsonPath("$.versions", hasSize(1)))
                .andExpect(jsonPath("$.versions[0].storageBucket").value("aegisflow-documents"))
                .andExpect(jsonPath("$.versions[0].storageObjectKey").exists())
                .andExpect(jsonPath("$.versions[0].extractedText").value("QRIS projects require expiry, reversal, and reconciliation requirements."));
    }

    @Test
    void addKnowledgeDocumentVersionAppendsImmutableVersion() throws Exception {
        MockMultipartFile initialFile = new MockMultipartFile(
                "file",
                "payment-nfr-v1.md",
                MediaType.TEXT_PLAIN_VALUE,
                "Payment projects require SLA and TPS requirements.".getBytes()
        );

        String response = mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(initialFile)
                        .param("sourceTitle", "Payment NFR")
                        .param("sourceId", "payment-nfr")
                        .param("sourceType", "REQUIREMENT_STANDARD")
                        .param("authority", "AUTHORITATIVE")
                        .param("allowedAgents", "Requirement Analyst Agent")
                        .param("workflowStates", "REQUIREMENT_ANALYSIS")
                        .param("tags", "payment,sla,tps"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String documentId = response.replaceAll(".*\\\"documentId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        MockMultipartFile revisedFile = new MockMultipartFile(
                "file",
                "payment-nfr-v2.md",
                MediaType.TEXT_PLAIN_VALUE,
                "Payment projects require SLA, TPS, latency, audit, and reconciliation requirements.".getBytes()
        );

        mockMvc.perform(multipart("/api/knowledge/documents/{documentId}/versions", documentId)
                        .file(revisedFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latestVersion").value(2))
                .andExpect(jsonPath("$.versions", hasSize(2)))
                .andExpect(jsonPath("$.versions[1].version").value(2))
                .andExpect(jsonPath("$.versions[1].fileName").value("payment-nfr-v2.md"));

        mockMvc.perform(get("/api/knowledge/documents/{documentId}/versions", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listKnowledgeDocumentsIncludesSeededAndUploadedDocuments() throws Exception {
        mockMvc.perform(get("/api/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))));
    }

    @Test
    void knowledgeScreenEndpointsExposeSourcesSearchSyncAndAudit() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "screen-source.md",
                MediaType.TEXT_PLAIN_VALUE,
                "Payment service catalog contains QRIS orchestration, reversal ownership, SLA metadata, and audit contacts.".getBytes()
        );

        mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(file)
                        .param("sourceTitle", "Screen Service Catalog")
                        .param("sourceId", "screen-service-catalog")
                        .param("sourceType", "SERVICE_CATALOG")
                        .param("authority", "AUTHORITATIVE")
                        .param("allowedAgents", "Requirement Analyst Agent,Architecture Agent")
                        .param("workflowStates", "REQUIREMENT_ANALYSIS,ARCHITECTURE_ANALYSIS")
                        .param("tags", "payment,qris,service-catalog"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/knowledge/sources/{sourceId}", "screen-service-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value("screen-service-catalog"))
                .andExpect(jsonPath("$.name").value("Screen Service Catalog"))
                .andExpect(jsonPath("$.status").value("FRESH"))
                .andExpect(jsonPath("$.documentCount").value(1))
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/knowledge/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSources").value(greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(6)));

        mockMvc.perform(get("/api/knowledge/search")
                        .param("q", "QRIS payment reversal SLA")
                        .param("agentName", "Requirement Analyst Agent")
                        .param("workflowState", "REQUIREMENT_ANALYSIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));

        mockMvc.perform(post("/api/knowledge/sources/{sourceId}/sync", "screen-service-catalog"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sourceId").value("screen-service-catalog"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/knowledge/sources/{sourceId}/sync-runs", "screen-service-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/knowledge/audit")
                        .param("sourceId", "screen-service-catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void knowledgeIngestionScreenCanRegisterConnectorAndDiscoverResources() throws Exception {
        mockMvc.perform(post("/api/knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "jira-delivery-history",
                                  "name": "Jira Delivery History",
                                  "sourceType": "PREVIOUS_PROJECT",
                                  "authority": "SUPPORTING",
                                  "ownerTeam": "Project Management Office",
                                  "freshnessSlaHours": 24,
                                  "syncMode": "SCHEDULED_PULL",
                                  "sensitivityPolicy": "INTERNAL",
                                  "allowedAgents": ["Requirement Analyst Agent", "Estimation Agent"],
                                  "workflowStates": ["REQUIREMENT_ANALYSIS", "ESTIMATION"],
                                  "tags": ["jira", "delivery-history"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceId").value("jira-delivery-history"));

        mockMvc.perform(get("/api/knowledge/adapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].adapterType", hasItem("JIRA")))
                .andExpect(jsonPath("$[*].adapterType", hasItem("GOOGLE_DRIVE")))
                .andExpect(jsonPath("$[*].adapterType", hasItem("GITLAB")));

        String response = mockMvc.perform(post("/api/knowledge/sources/{sourceId}/connections", "jira-delivery-history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adapterType": "JIRA",
                                  "connectionName": "PMO Jira historical delivery filter",
                                  "resourceLocator": "project = PAY AND type in (Epic, Story)",
                                  "authType": "PAT",
                                  "credentialRef": "vault://knowledge/jira/pmo-readonly",
                                  "configJson": "{\\"baseUrl\\":\\"https://jira.example.test\\",\\"jql\\":\\"project = PAY AND type in (Epic, Story)\\"}"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceId").value("jira-delivery-history"))
                .andExpect(jsonPath("$.adapterType").value("JIRA"))
                .andExpect(jsonPath("$.connectionStatus").value("CONFIGURED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String connectionId = response.replaceAll(".*\\\"connectionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/knowledge/connections/{connectionId}/check", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"));

        mockMvc.perform(post("/api/knowledge/connections/{connectionId}/discover", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].connectionId").value(connectionId))
                .andExpect(jsonPath("$[0].status").value("DISCOVERED"));

        mockMvc.perform(get("/api/knowledge/connections/{connectionId}/resources", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/knowledge/audit")
                        .param("sourceId", "jira-delivery-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
    }

    @Test
    void sourceSyncUsesAdapterPayloadsAndSkipsUnchangedResources() throws Exception {
        mockMvc.perform(post("/api/knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "test-sync-source",
                                  "name": "Test Sync Source",
                                  "sourceType": "PREVIOUS_PROJECT",
                                  "authority": "SUPPORTING",
                                  "ownerTeam": "Architecture",
                                  "freshnessSlaHours": 24,
                                  "syncMode": "SCHEDULED_PULL",
                                  "sensitivityPolicy": "INTERNAL",
                                  "allowedAgents": ["Requirement Analyst Agent"],
                                  "workflowStates": ["REQUIREMENT_ANALYSIS"],
                                  "tags": ["sync-test"]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/knowledge/sources/{sourceId}/connections", "test-sync-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adapterType": "TEST_SYNC",
                                  "connectionName": "Deterministic test adapter",
                                  "resourceLocator": "test-resource-space",
                                  "authType": "NONE",
                                  "configJson": "{}"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/knowledge/sources/{sourceId}/sync", "test-sync-source"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsChanged").value(1));

        mockMvc.perform(get("/api/knowledge/sources/{sourceId}", "test-sync-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FRESH"))
                .andExpect(jsonPath("$.documentCount").value(1))
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/knowledge/search")
                        .param("q", "deterministic adapter resource reversal")
                        .param("agentName", "Requirement Analyst Agent")
                        .param("workflowState", "REQUIREMENT_ANALYSIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));

        mockMvc.perform(post("/api/knowledge/sources/{sourceId}/sync", "test-sync-source"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.recordsChanged").value(0));
    }

    @Test
    void createKnowledgeDocumentIndexesGoSourceWithTreeSitterChunks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payment_orchestrator.go",
                "text/x-go",
                """
                        package payment

                        type PaymentRepository interface {
                            Save(reference string) error
                        }

                        type PaymentService struct {
                            repository PaymentRepository
                        }

                        func (service PaymentService) CreateDynamicQr(reference string) error {
                            return service.repository.Save(reference)
                        }

                        func normalizeReference(reference string) string {
                            return "QRIS-" + reference
                        }
                        """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/knowledge/documents")
                        .file(file)
                        .param("sourceTitle", "Payment Orchestrator Source")
                        .param("sourceId", "payment-orchestrator-source")
                        .param("sourceType", "SOURCE_CODE")
                        .param("authority", "SUPPORTING")
                        .param("allowedAgents", "Requirement Analyst Agent,Architecture Agent")
                        .param("workflowStates", "REQUIREMENT_ANALYSIS,ARCHITECTURE_ANALYSIS")
                        .param("tags", "go,payment,qris"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versions[0].extractedText", containsString("CreateDynamicQr")));

        mockMvc.perform(get("/api/knowledge/search")
                        .param("q", "CreateDynamicQr PaymentRepository dynamic QR")
                        .param("agentName", "Architecture Agent")
                        .param("workflowState", "ARCHITECTURE_ANALYSIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[*].sourceTitle", hasItem("Payment Orchestrator Source")))
                .andExpect(jsonPath("$[*].excerpt", hasItem(containsString("CreateDynamicQr"))));
    }

    @Test
    void resourceContentEndpointReturnsFetchedAdapterPayload() throws Exception {
        mockMvc.perform(post("/api/knowledge/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceId": "test-resource-content-source",
                                  "name": "Test Resource Content Source",
                                  "sourceType": "PREVIOUS_PROJECT",
                                  "authority": "SUPPORTING",
                                  "ownerTeam": "Architecture",
                                  "freshnessSlaHours": 24,
                                  "syncMode": "MANUAL_PULL",
                                  "sensitivityPolicy": "INTERNAL",
                                  "allowedAgents": ["Requirement Analyst Agent"],
                                  "workflowStates": ["REQUIREMENT_ANALYSIS"],
                                  "tags": ["resource-content-test"]
                                }
                                """))
                .andExpect(status().isCreated());

        String connectionResponse = mockMvc.perform(post("/api/knowledge/sources/{sourceId}/connections", "test-resource-content-source")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adapterType": "TEST_SYNC",
                                  "connectionName": "Deterministic resource content adapter",
                                  "resourceLocator": "test-resource-space",
                                  "authType": "NONE",
                                  "configJson": "{}"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String connectionId = connectionResponse.replaceAll(".*\\\"connectionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        String discoverResponse = mockMvc.perform(post("/api/knowledge/connections/{connectionId}/discover", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String resourceId = discoverResponse.replaceAll(".*\\\"resourceId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/knowledge/resources/{resourceId}/content", resourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("deterministic-resource.md"))
                .andExpect(jsonPath("$.mediaType").value("text/markdown"))
                .andExpect(jsonPath("$.contentBase64").exists())
                .andExpect(jsonPath("$.versionRef").value("v1"));
    }

    @TestConfiguration
    static class TestKnowledgeAdapterConfiguration {
        @Bean
        KnowledgeSourceAdapter testSyncKnowledgeSourceAdapter() {
            return new KnowledgeSourceAdapter() {
                @Override
                public KnowledgeAdapterDescriptor descriptor() {
                    return new KnowledgeAdapterDescriptor(
                            "TEST_SYNC",
                            "Test Sync",
                            "Deterministic test adapter",
                            List.of("NONE"),
                            List.of("DOCUMENT"),
                            List.of(),
                            true
                    );
                }

                @Override
                public KnowledgeConnectionCheckResult checkConnection(KnowledgeSourceConnection connection, Instant checkedAt) {
                    return new KnowledgeConnectionCheckResult("CONNECTED", "Test adapter connected", checkedAt);
                }

                @Override
                public List<KnowledgeSourceResource> discoverResources(KnowledgeSourceConnection connection, Instant discoveredAt) {
                    return List.of(new KnowledgeSourceResource(
                            UUID.randomUUID(),
                            connection.connectionId(),
                            "test-resource-1",
                            "DOCUMENT",
                            "Deterministic Adapter Resource",
                            "test://resource-1",
                            "v1",
                            "test-hash",
                            "DISCOVERED",
                            discoveredAt
                    ));
                }

                @Override
                public Optional<KnowledgeSourcePayload> fetchResource(KnowledgeSourceConnection connection, KnowledgeSourceResource resource) {
                    return Optional.of(new KnowledgeSourcePayload(
                            "deterministic-resource.md",
                            "text/markdown",
                            "Deterministic adapter resource includes reversal and reconciliation lessons.".getBytes(StandardCharsets.UTF_8),
                            "v1"
                    ));
                }
            };
        }
    }
}
