package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.WorkflowState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ControlPanelReadModelControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ReviewService reviewService;

    @Test
    void exposesControlPanelReadModelsFromPersistedProjectReviewAgentAndArtifactData() throws Exception {
        var project = projectService.submitBrd(
                "Dynamic QRIS Payment Read Model",
                "Payments Business Team",
                "Merchant Payments",
                new SubmittedDocument(
                        "dynamic-qris-read-model.md",
                        MediaType.TEXT_MARKDOWN_VALUE,
                        "BRD for read-model testing".getBytes(StandardCharsets.UTF_8)
                )
        );
        projectService.addGeneratedArtifact(
                project.projectId(),
                ArtifactType.JIRA_PLAN,
                "jira-plan-read-model.json",
                MediaType.APPLICATION_JSON_VALUE,
                """
                        {
                          "epic": {"summary": "Dynamic QRIS Payment"},
                          "stories": []
                        }
                        """.getBytes(StandardCharsets.UTF_8)
        ).orElseThrow();
        projectService.saveAgentExecution(
                project.projectId(),
                WorkflowState.REQUIREMENT_ANALYSIS,
                "Requirement Analyst Agent",
                "0.1.0",
                "requirement-analysis-v1",
                "local-deterministic",
                AgentExecutionStatus.SUCCEEDED,
                0.91,
                120,
                80,
                Instant.parse("2026-10-13T01:00:00Z"),
                Instant.parse("2026-10-13T01:00:02Z"),
                "Requirement analysis completed"
        );
        var review = reviewService.createPmReview(project.projectId()).orElseThrow();

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProjects").value(1))
                .andExpect(jsonPath("$.pendingReviews").value(1))
                .andExpect(jsonPath("$.pendingPmApprovals").value(1))
                .andExpect(jsonPath("$.recentProjects[0].projectId").value(project.projectId().toString()))
                .andExpect(jsonPath("$.recentAgentRuns[0].agentName").value("Requirement Analyst Agent"));

        mockMvc.perform(get("/api/projects")
                        .param("state", "HUMAN_PM_APPROVAL")
                        .param("domain", "Merchant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectId").value(project.projectId().toString()))
                .andExpect(jsonPath("$[0].pendingReview.reviewId").value(review.reviewId().toString()));

        mockMvc.perform(get("/api/projects/{projectId}/room", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.projectId").value(project.projectId().toString()))
                .andExpect(jsonPath("$.artifacts[*].artifactType", hasItem("BRD")))
                .andExpect(jsonPath("$.artifacts[*].artifactType", hasItem("JIRA_PLAN")))
                .andExpect(jsonPath("$.reviews[0].reviewId").value(review.reviewId().toString()))
                .andExpect(jsonPath("$.agentRuns[0].agentName").value("Requirement Analyst Agent"))
                .andExpect(jsonPath("$.jiraDraft.fileName").value("jira-plan-read-model.json"))
                .andExpect(jsonPath("$.activity[*].eventType", hasItem("PROJECT_CREATED")));

        mockMvc.perform(get("/api/reviews/my-queue")
                        .param("role", "Project Management"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reviewId").value(review.reviewId().toString()));

        String agentRunsResponse = mockMvc.perform(get("/api/agent-runs")
                        .param("projectId", project.projectId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].agentName").value("Requirement Analyst Agent"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String executionId = agentRunsResponse.replaceAll(".*\\\"executionId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/agent-runs/{executionId}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.executionId").value(executionId))
                .andExpect(jsonPath("$.projectArtifacts[*].artifactType", hasItem("JIRA_PLAN")))
                .andExpect(jsonPath("$.relatedReviews[0].reviewId").value(review.reviewId().toString()));

        mockMvc.perform(get("/api/jira-drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(project.projectId().toString()))
                .andExpect(jsonPath("$[0].readyForPmApproval").value(true));

        mockMvc.perform(get("/api/jira-drafts/{projectId}", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.fileName").value("jira-plan-read-model.json"))
                .andExpect(jsonPath("$.pmReviews[0].reviewId").value(review.reviewId().toString()));

        mockMvc.perform(get("/api/audit/events")
                        .param("projectId", project.projectId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItem("PROJECT_CREATED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("ARTIFACT_VERSION_CREATED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("AGENT_EXECUTION_SUCCEEDED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("HUMAN_REVIEW_PENDING")));

        mockMvc.perform(get("/api/operations/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runningWorkflows").value(1))
                .andExpect(jsonPath("$.waitingForPmApproval").value(1))
                .andExpect(jsonPath("$.agentRunsByStatus.SUCCEEDED").value(1));

        mockMvc.perform(get("/api/operations/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(project.projectId().toString()))
                .andExpect(jsonPath("$[0].operationalStatus").value("WAITING_FOR_PM_APPROVAL"));
    }
}
