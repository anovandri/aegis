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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        projectService.submitBrdVersion(
                project.projectId(),
                new SubmittedDocument(
                        "dynamic-qris-read-model-v2.md",
                        MediaType.TEXT_MARKDOWN_VALUE,
                        "Updated BRD that makes existing generated artifacts stale".getBytes(StandardCharsets.UTF_8)
                )
        ).orElseThrow();

        mockMvc.perform(post("/api/project-rooms/{projectId}/messages", project.projectId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "business.nadia",
                                  "body": "Please review the revised BRD with @pm.dimas.",
                                  "anchorType": "ARTIFACT",
                                  "anchorId": "BRD:v2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actor").value("business.nadia"))
                .andExpect(jsonPath("$.mentions", hasItem("pm.dimas")))
                .andExpect(jsonPath("$.anchorType").value("ARTIFACT"));

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
                .andExpect(jsonPath("$[0].pendingReview.reviewId").value(review.reviewId().toString()))
                .andExpect(jsonPath("$[0].hasStaleArtifacts").value(true))
                .andExpect(jsonPath("$[0].riskFlags", hasItem("STALE_ARTIFACTS")));

        mockMvc.perform(get("/api/projects")
                        .param("stale", "true")
                        .param("risk", "STALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].projectId").value(project.projectId().toString()));

        mockMvc.perform(get("/api/project-rooms/{projectId}", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.projectId").value(project.projectId().toString()))
                .andExpect(jsonPath("$.artifacts[*].artifactType", hasItem("BRD")))
                .andExpect(jsonPath("$.artifacts[*].artifactType", hasItem("JIRA_PLAN")))
                .andExpect(jsonPath("$.reviews[0].reviewId").value(review.reviewId().toString()))
                .andExpect(jsonPath("$.agentRuns[0].agentName").value("Requirement Analyst Agent"))
                .andExpect(jsonPath("$.jiraDraft.fileName").value("jira-plan-read-model.json"))
                .andExpect(jsonPath("$.messages[0].body").value("Please review the revised BRD with @pm.dimas."))
                .andExpect(jsonPath("$.activity[*].eventType", hasItem("PROJECT_CREATED")));

        mockMvc.perform(get("/api/project-rooms/{projectId}/artifacts", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].artifactType", hasItem("JIRA_PLAN")))
                .andExpect(jsonPath("$[?(@.artifactType == 'JIRA_PLAN')].stale", hasItem(true)))
                .andExpect(jsonPath("$[?(@.artifactType == 'JIRA_PLAN')].staleReasons[0]", hasItem("A newer BRD version exists after this artifact was generated")));

        mockMvc.perform(get("/api/project-rooms/{projectId}/messages", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].mentions", hasItem("pm.dimas")));

        mockMvc.perform(get("/api/project-rooms/{projectId}/activity", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItem("PROJECT_MESSAGE_ADDED")));

        mockMvc.perform(get("/api/project-rooms/{projectId}/suggested-actions", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("OPEN_PENDING_REVIEW")))
                .andExpect(jsonPath("$", hasItem("UPLOAD_REVISED_BRD_OR_RERUN_ANALYSIS")));

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
                .andExpect(jsonPath("$[*].eventType", hasItem("HUMAN_REVIEW_PENDING")))
                .andExpect(jsonPath("$[*].eventType", hasItem("PROJECT_MESSAGE_ADDED")));

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
