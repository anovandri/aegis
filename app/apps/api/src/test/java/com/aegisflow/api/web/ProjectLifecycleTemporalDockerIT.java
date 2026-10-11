package com.aegisflow.api.web;

import com.aegisflow.api.domain.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.serviceclient.WorkflowServiceStubs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectLifecycleTemporalDockerIT {
    private static final String TASK_QUEUE = "aegisflow-project-lifecycle-it";
    private static final Network NETWORK = Network.newNetwork();

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("aegisflow")
            .withUsername("aegisflow")
            .withPassword("aegisflow-secret")
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres");

    @Container
    private static final GenericContainer<?> TEMPORAL = new GenericContainer<>(
            DockerImageName.parse("temporalio/auto-setup:1.28.1")
    )
            .withNetwork(NETWORK)
            .dependsOn(POSTGRES)
            .withEnv("DB", "postgres12")
            .withEnv("POSTGRES_SEEDS", "postgres")
            .withEnv("DB_PORT", "5432")
            .withEnv("POSTGRES_USER", "aegisflow")
            .withEnv("POSTGRES_PWD", "aegisflow-secret")
            .withEnv("DBNAME", "temporal")
            .withEnv("VISIBILITY_DBNAME", "temporal_visibility")
            .withEnv("DEFAULT_NAMESPACE", "default")
            .withEnv("SKIP_ADD_CUSTOM_SEARCH_ATTRIBUTES", "true")
            .withCommand("autosetup")
            .withExposedPorts(7233)
            .waitingFor(Wait.forLogMessage(".*Default namespace default registration complete\\..*", 1)
                    .withStartupTimeout(Duration.ofSeconds(120)));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowServiceStubs workflowServiceStubs;

    @DynamicPropertySource
    static void dockerBackedProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/vendor/postgresql");
        registry.add("aegisflow.storage.provider", () -> "in-memory");
        registry.add("aegisflow.temporal.enabled", () -> "true");
        registry.add("aegisflow.temporal.target", () -> "%s:%d".formatted(TEMPORAL.getHost(), TEMPORAL.getMappedPort(7233)));
        registry.add("aegisflow.temporal.namespace", () -> "default");
        registry.add("aegisflow.temporal.task-queue", () -> TASK_QUEUE);
        registry.add("aegisflow.llm.provider", () -> "local");
        registry.add("aegisflow.knowledge.embedding.provider", () -> "local");
    }

    @BeforeEach
    void waitForTemporalNamespace() throws Exception {
        waitUntil("Temporal default namespace to be queryable", () -> {
            workflowServiceStubs.blockingStub().describeNamespace(DescribeNamespaceRequest.newBuilder()
                    .setNamespace("default")
                    .build());
            return Boolean.TRUE;
        });
    }

    @Test
    void workflowWaitsForReviewSignalsAndCompletesAfterTechnicalAndPmApproval() throws Exception {
        UUID projectId = submitProject("Temporal Happy Path Review");

        JsonNode technicalProgress = waitForProgress(projectId, "WAITING_FOR_TECHNICAL_REVIEW");
        assertThat(technicalProgress.get("currentState").asText()).isEqualTo("HUMAN_TECHNICAL_REVIEW");
        UUID technicalReviewId = waitForReview(projectId, "TECHNICAL_REVIEW", "PENDING");
        assertProjectState(projectId, WorkflowState.HUMAN_TECHNICAL_REVIEW);

        submitDecision(
                technicalReviewId,
                """
                        {
                          "decision": "APPROVE_WITH_CHANGE",
                          "actor": "architect-temporal-001",
                          "correction": "Reuse existing orchestration capability and keep timeout handling explicit."
                        }
                        """
        );

        JsonNode pmProgress = waitForProgress(projectId, "WAITING_FOR_PM_APPROVAL");
        assertThat(pmProgress.get("currentState").asText()).isEqualTo("HUMAN_PM_APPROVAL");
        assertArtifact(projectId, "ARCHITECTURE_ANALYSIS");
        assertArtifact(projectId, "SYSTEM_ANALYSIS");
        assertArtifact(projectId, "ESTIMATION");
        assertArtifact(projectId, "JIRA_PLAN");
        UUID pmReviewId = waitForReview(projectId, "PM_APPROVAL", "PENDING");

        submitDecision(
                pmReviewId,
                """
                        {
                          "decision": "APPROVE",
                          "actor": "pm-temporal-001"
                        }
                        """
        );

        JsonNode completedProgress = waitForProgress(projectId, "COMPLETED");
        assertThat(completedProgress.get("currentState").asText()).isEqualTo("JIRA_CREATED");
        assertProjectState(projectId, WorkflowState.JIRA_CREATED);
        assertDecisionCount(projectId, 2);
    }

    @Test
    void workflowReceivesTechnicalRejectSignalAndReturnsToArchitectureAnalysis() throws Exception {
        UUID projectId = submitProject("Temporal Technical Reject Review");

        waitForProgress(projectId, "WAITING_FOR_TECHNICAL_REVIEW");
        UUID technicalReviewId = waitForReview(projectId, "TECHNICAL_REVIEW", "PENDING");

        submitDecision(
                technicalReviewId,
                """
                        {
                          "decision": "REJECT",
                          "actor": "architect-temporal-002",
                          "rejectionReason": "Architecture analysis must prove service reuse before estimation."
                        }
                        """
        );

        JsonNode rejectedProgress = waitForProgress(projectId, "TECHNICAL_REVIEW_REJECTED");
        assertThat(rejectedProgress.get("currentState").asText()).isEqualTo("ARCHITECTURE_ANALYSIS");
        assertProjectState(projectId, WorkflowState.ARCHITECTURE_ANALYSIS);
        assertNoArtifact(projectId, "ESTIMATION");
        assertDecisionCount(projectId, 1);
    }

    private UUID submitProject(String name) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "brdFile",
                "%s.md".formatted(name.toLowerCase().replace(" ", "-")),
                "text/markdown",
                """
                        # Dynamic Payment Capability
                        Business objective: enable a controlled payment capability.
                        Functional requirements: create payment, confirm payment, reconcile payment.
                        Non-functional requirements: SLA, timeout handling, retry behavior, audit trail, and security controls.
                        """.getBytes(StandardCharsets.UTF_8)
        );

        String response = mockMvc.perform(multipart("/api/projects")
                        .file(file)
                        .param("name", name)
                        .param("businessOwner", "Payments Business Team")
                        .param("domain", "Payments"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("projectId").asText());
    }

    private JsonNode waitForProgress(UUID projectId, String expectedStatus) throws Exception {
        return waitUntil("workflow status %s for project %s".formatted(expectedStatus, projectId), () -> {
            String response = mockMvc.perform(get("/api/projects/{projectId}/workflow", projectId))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode progress = objectMapper.readTree(response);
            if (progress.has("status") && expectedStatus.equals(progress.get("status").asText())) {
                return progress;
            }
            return null;
        });
    }

    private UUID waitForReview(UUID projectId, String reviewType, String status) throws Exception {
        JsonNode review = waitUntil("%s review with status %s for project %s".formatted(reviewType, status, projectId), () -> {
            String response = mockMvc.perform(get("/api/reviews")
                            .param("reviewType", reviewType)
                            .param("status", status))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode reviews = objectMapper.readTree(response);
            for (JsonNode candidate : reviews) {
                if (projectId.toString().equals(candidate.get("projectId").asText())) {
                    return candidate;
                }
            }
            return null;
        });
        return UUID.fromString(review.get("reviewId").asText());
    }

    private void submitDecision(UUID reviewId, String payload) throws Exception {
        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", reviewId)
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    private <T> T waitUntil(String description, ThrowingSupplier<T> supplier) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        Throwable lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                T value = supplier.get();
                if (value != null) {
                    return value;
                }
            } catch (Throwable throwable) {
                lastFailure = throwable;
            }
            Thread.sleep(500);
        }
        AssertionError timeout = new AssertionError("Timed out waiting for " + description);
        if (lastFailure != null) {
            timeout.initCause(lastFailure);
        }
        throw timeout;
    }

    private void assertProjectState(UUID projectId, WorkflowState expectedState) {
        String actualState = jdbcTemplate.queryForObject(
                "SELECT current_state FROM projects WHERE project_id = ?",
                String.class,
                projectId
        );
        assertThat(actualState).isEqualTo(expectedState.name());
    }

    private void assertArtifact(UUID projectId, String artifactType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artifact_versions WHERE project_id = ? AND artifact_type = ?",
                Integer.class,
                projectId,
                artifactType
        );
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    private void assertNoArtifact(UUID projectId, String artifactType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artifact_versions WHERE project_id = ? AND artifact_type = ?",
                Integer.class,
                projectId,
                artifactType
        );
        assertThat(count).isZero();
    }

    private void assertDecisionCount(UUID projectId, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM human_review_decisions WHERE project_id = ?",
                Integer.class,
                projectId
        );
        assertThat(count).isEqualTo(expectedCount);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
