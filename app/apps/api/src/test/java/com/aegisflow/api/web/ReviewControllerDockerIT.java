package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.domain.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReviewControllerDockerIT {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres")
    )
            .withDatabaseName("aegisflow")
            .withUsername("aegisflow")
            .withPassword("aegisflow-secret");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void dockerBackedProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/vendor/postgresql");
        registry.add("aegisflow.storage.provider", () -> "in-memory");
        registry.add("aegisflow.temporal.enabled", () -> "false");
        registry.add("aegisflow.llm.provider", () -> "local");
        registry.add("aegisflow.knowledge.embedding.provider", () -> "local");
    }

    @Test
    void reviewQueueLifecyclePersistsAssignmentsDecisionsStateTransitionsAndGeneratedArtifacts() throws Exception {
        var technicalProject = projectService.submitBrd(
                "Docker Technical Review Project",
                "Payments Business Team",
                "Payments",
                new SubmittedDocument(
                        "docker-technical-brd.md",
                        "text/markdown",
                        "BRD requiring technical review.".getBytes(StandardCharsets.UTF_8)
                )
        );
        var technicalReview = reviewService.createTechnicalReview(technicalProject.projectId()).orElseThrow();

        assertReviewRow(technicalReview.reviewId(), technicalProject.projectId(), "TECHNICAL_REVIEW", "PENDING");
        assertProjectState(technicalProject.projectId(), WorkflowState.HUMAN_TECHNICAL_REVIEW);

        mockMvc.perform(get("/api/reviews")
                        .param("status", "PENDING")
                        .param("reviewType", "TECHNICAL_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].reviewId", hasItem(technicalReview.reviewId().toString())));

        mockMvc.perform(post("/api/reviews/{reviewId}/assign", technicalReview.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignee": "architect-docker-001",
                                  "dueAt": "2026-10-11T03:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee").value("architect-docker-001"));
        assertAssignmentWasPersisted(technicalReview.reviewId(), "architect-docker-001", "ASSIGNED");

        String decisionResponse = mockMvc.perform(post("/api/reviews/{reviewId}/decisions", technicalReview.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE_WITH_CHANGE",
                                  "actor": "architect-docker-001",
                                  "correction": "Reuse Payment Orchestration Service and publish asynchronous reversal events."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("APPROVE_WITH_CHANGE"))
                .andExpect(jsonPath("$.previousState").value("HUMAN_TECHNICAL_REVIEW"))
                .andExpect(jsonPath("$.nextState").value("ESTIMATION"))
                .andExpect(jsonPath("$.generatedArtifactId").exists())
                .andExpect(jsonPath("$.generatedArtifactVersion").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode technicalDecisionJson = objectMapper.readTree(decisionResponse);
        UUID technicalDecisionId = UUID.fromString(technicalDecisionJson.get("decisionId").asText());
        UUID generatedArtifactId = UUID.fromString(technicalDecisionJson.get("generatedArtifactId").asText());

        assertReviewStatus(technicalReview.reviewId(), "DECIDED");
        assertDecisionRow(technicalDecisionId, technicalReview.reviewId(), technicalProject.projectId(), "APPROVE_WITH_CHANGE", "ESTIMATION");
        assertGeneratedArtifactWasPersisted(technicalProject.projectId(), generatedArtifactId, "ARCHITECTURE_ANALYSIS", 1);
        assertProjectState(technicalProject.projectId(), WorkflowState.ESTIMATION);

        var clarificationProject = projectService.submitBrd(
                "Docker Clarification Project",
                "Risk Business Team",
                "Risk",
                new SubmittedDocument("docker-clarification-brd.md", "text/markdown", "BRD requiring clarification.".getBytes(StandardCharsets.UTF_8))
        );
        var clarificationReview = reviewService.createTechnicalReview(clarificationProject.projectId()).orElseThrow();

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", clarificationReview.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REQUEST_CLARIFICATION",
                                  "actor": "architect-docker-002",
                                  "clarificationQuestions": ["What is peak TPS?", "What is the retry policy?"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("REQUEST_CLARIFICATION"))
                .andExpect(jsonPath("$.nextState").value("HUMAN_TECHNICAL_REVIEW"))
                .andExpect(jsonPath("$.clarificationQuestions", hasSize(2)));
        assertProjectState(clarificationProject.projectId(), WorkflowState.HUMAN_TECHNICAL_REVIEW);
        assertClarificationQuestionsWerePersisted(clarificationReview.reviewId(), "What is peak TPS?", "What is the retry policy?");

        var pmProject = projectService.submitBrd(
                "Docker PM Approval Project",
                "Operations Business Team",
                "Operations",
                new SubmittedDocument("docker-pm-brd.md", "text/markdown", "BRD requiring PM approval.".getBytes(StandardCharsets.UTF_8))
        );
        var pmReview = reviewService.createPmReview(pmProject.projectId()).orElseThrow();

        String pmDecisionResponse = mockMvc.perform(post("/api/reviews/{reviewId}/decisions", pmReview.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "actor": "pm-docker-001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.nextState").value("JIRA_CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID pmDecisionId = UUID.fromString(objectMapper.readTree(pmDecisionResponse).get("decisionId").asText());
        assertDecisionRow(pmDecisionId, pmReview.reviewId(), pmProject.projectId(), "APPROVE", "JIRA_CREATED");
        assertProjectState(pmProject.projectId(), WorkflowState.JIRA_CREATED);

        mockMvc.perform(get("/api/reviews")
                        .param("status", "DECIDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].reviewId", hasItem(technicalReview.reviewId().toString())))
                .andExpect(jsonPath("$[*].reviewId", hasItem(clarificationReview.reviewId().toString())))
                .andExpect(jsonPath("$[*].reviewId", hasItem(pmReview.reviewId().toString())));

        mockMvc.perform(get("/api/reviews/{reviewId}/decisions", technicalReview.reviewId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].decision").value("APPROVE_WITH_CHANGE"));
    }

    @Test
    void reviewQueueRejectPathIsPersistedInPostgres() throws Exception {
        var project = projectService.submitBrd(
                "Docker Rejection Project",
                "Compliance Business Team",
                "Compliance",
                new SubmittedDocument("docker-reject-brd.md", "text/markdown", "BRD requiring rejection.".getBytes(StandardCharsets.UTF_8))
        );
        var review = reviewService.createPmReview(project.projectId()).orElseThrow();

        String response = mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECT",
                                  "actor": "pm-docker-002",
                                  "rejectionReason": "Capacity assumptions are incomplete."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.nextState").value("JIRA_DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID decisionId = UUID.fromString(objectMapper.readTree(response).get("decisionId").asText());

        assertReviewStatus(review.reviewId(), "DECIDED");
        assertDecisionRow(decisionId, review.reviewId(), project.projectId(), "REJECT", "JIRA_DRAFT");
        assertProjectState(project.projectId(), WorkflowState.JIRA_DRAFT);
        String rejectionReason = jdbcTemplate.queryForObject(
                "SELECT rejection_reason FROM human_review_decisions WHERE decision_id = ?",
                String.class,
                decisionId
        );
        assertThat(rejectionReason).isEqualTo("Capacity assumptions are incomplete.");
    }

    private void assertReviewRow(UUID reviewId, UUID projectId, String reviewType, String status) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM human_reviews WHERE review_id = ? AND project_id = ? AND review_type = ? AND status = ?",
                Integer.class,
                reviewId,
                projectId,
                reviewType,
                status
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertReviewStatus(UUID reviewId, String status) {
        String actualStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM human_reviews WHERE review_id = ?",
                String.class,
                reviewId
        );
        assertThat(actualStatus).isEqualTo(status);
    }

    private void assertAssignmentWasPersisted(UUID reviewId, String assignee, String status) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM human_reviews WHERE review_id = ? AND assignee = ? AND status = ? AND due_at IS NOT NULL",
                Integer.class,
                reviewId,
                assignee,
                status
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertDecisionRow(UUID decisionId, UUID reviewId, UUID projectId, String decision, String nextState) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM human_review_decisions
                        WHERE decision_id = ?
                          AND review_id = ?
                          AND project_id = ?
                          AND decision = ?
                          AND next_state = ?
                        """,
                Integer.class,
                decisionId,
                reviewId,
                projectId,
                decision,
                nextState
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertGeneratedArtifactWasPersisted(UUID projectId, UUID artifactId, String artifactType, int version) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM artifact_versions
                        WHERE project_id = ?
                          AND artifact_id = ?
                          AND artifact_type = ?
                          AND version = ?
                          AND file_name LIKE 'human-review-correction-%'
                        """,
                Integer.class,
                projectId,
                artifactId,
                artifactType,
                version
        );
        assertThat(count).isEqualTo(1);
    }

    private void assertClarificationQuestionsWerePersisted(UUID reviewId, String... expectedQuestions) {
        String questions = jdbcTemplate.queryForObject(
                "SELECT clarification_questions FROM human_review_decisions WHERE review_id = ?",
                String.class,
                reviewId
        );
        assertThat(questions).contains(expectedQuestions);
    }

    private void assertProjectState(UUID projectId, WorkflowState expectedState) {
        String actualState = jdbcTemplate.queryForObject(
                "SELECT current_state FROM projects WHERE project_id = ?",
                String.class,
                projectId
        );
        assertThat(actualState).isEqualTo(expectedState.name());
    }
}
