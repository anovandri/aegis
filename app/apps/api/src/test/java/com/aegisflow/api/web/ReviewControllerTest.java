package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.application.SubmittedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ReviewService reviewService;

    @Test
    void reviewQueueListsOpensAssignsAndApprovesWithChange() throws Exception {
        var project = projectService.submitBrd(
                "Dynamic QRIS Payment",
                "Payments Business Team",
                "Merchant Payments",
                new SubmittedDocument(
                        "dynamic-qris-brd-v1.md",
                        "text/markdown",
                        "Dynamic QRIS payment BRD".getBytes(StandardCharsets.UTF_8)
                )
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();

        mockMvc.perform(get("/api/reviews")
                        .param("status", "PENDING")
                        .param("reviewType", "TECHNICAL_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].reviewId", hasItem(review.reviewId().toString())))
                .andExpect(jsonPath("$[*].projectName", hasItem("Dynamic QRIS Payment")));

        mockMvc.perform(get("/api/reviews/{reviewId}", review.reviewId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewType").value("TECHNICAL_REVIEW"))
                .andExpect(jsonPath("$.workflowState").value("HUMAN_TECHNICAL_REVIEW"))
                .andExpect(jsonPath("$.blockingQuestions", not(empty())));

        mockMvc.perform(post("/api/reviews/{reviewId}/assign", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignee": "user-architect-001",
                                  "dueAt": "2026-10-11T03:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee").value("user-architect-001"));

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE_WITH_CHANGE",
                                  "actor": "user-architect-001",
                                  "correction": "Reuse Payment Orchestration Service but require asynchronous reversal event publishing and explicit issuer timeout handling."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("APPROVE_WITH_CHANGE"))
                .andExpect(jsonPath("$.previousState").value("HUMAN_TECHNICAL_REVIEW"))
                .andExpect(jsonPath("$.nextState").value("ESTIMATION"))
                .andExpect(jsonPath("$.generatedArtifactId").exists())
                .andExpect(jsonPath("$.generatedArtifactVersion").value(1));

        mockMvc.perform(get("/api/projects/{projectId}", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentState").value("ESTIMATION"));

        mockMvc.perform(get("/api/projects/{projectId}/context", project.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifacts[*].artifactType", hasItem("ARCHITECTURE_ANALYSIS")));

        mockMvc.perform(get("/api/reviews/{reviewId}/decisions", review.reviewId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actor").value("user-architect-001"));

        mockMvc.perform(post("/api/reviews/{reviewId}/assign", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignee": "another-user"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void bulkAssignUpdatesMultipleOpenReviews() throws Exception {
        var first = projectService.submitBrd(
                "Merchant Limit Update",
                "Payments Business Team",
                "Merchant Payments",
                new SubmittedDocument("merchant-limit.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var second = projectService.submitBrd(
                "Wallet Ledger Sync",
                "Wallet Business Team",
                "Wallet",
                new SubmittedDocument("wallet-ledger.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var firstReview = reviewService.createTechnicalReview(first.projectId()).orElseThrow();
        var secondReview = reviewService.createPmReview(second.projectId()).orElseThrow();

        mockMvc.perform(post("/api/reviews/bulk-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewIds": ["%s", "%s"],
                                  "assignee": "review-lead-001",
                                  "dueAt": "2026-10-11T05:00:00Z"
                                }
                                """.formatted(firstReview.reviewId(), secondReview.reviewId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].assignee", hasItem("review-lead-001")))
                .andExpect(jsonPath("$[*].status", hasItem("ASSIGNED")));
    }

    @Test
    void approveWithChangeRequiresCorrection() throws Exception {
        var project = projectService.submitBrd(
                "Dispute Evidence Upload",
                "Operations Team",
                "Dispute",
                new SubmittedDocument("dispute.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE_WITH_CHANGE",
                                  "actor": "user-architect-001"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decisionRequestRequiresDecisionAndActor() throws Exception {
        var project = projectService.submitBrd(
                "Merchant Onboarding Risk Review",
                "Risk Team",
                "Merchant Risk",
                new SubmittedDocument("merchant-risk.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actor": "user-architect-001"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestClarificationAndRejectHaveRequiredPayloadRules() throws Exception {
        var project = projectService.submitBrd(
                "Issuer Timeout Alignment",
                "Payments Business Team",
                "Payments",
                new SubmittedDocument("issuer-timeout.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var review = reviewService.createTechnicalReview(project.projectId()).orElseThrow();

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REQUEST_CLARIFICATION",
                                  "actor": "user-architect-001"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECT",
                                  "actor": "user-architect-001"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notFoundAndConflictResponsesAreReturnedForReviewActions() throws Exception {
        var project = projectService.submitBrd(
                "Settlement Report Automation",
                "Finance Team",
                "Settlement",
                new SubmittedDocument("settlement.md", "text/markdown", "BRD".getBytes(StandardCharsets.UTF_8))
        );
        var review = reviewService.createPmReview(project.projectId()).orElseThrow();

        mockMvc.perform(get("/api/reviews/{reviewId}", "4ce5d038-2d50-4c58-a7d7-7a0d9f7f2b88"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "actor": "pm-001"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reviews/{reviewId}/decisions", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "actor": "pm-001"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/reviews/{reviewId}/assign", review.reviewId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignee": "pm-002"
                                }
                                """))
                .andExpect(status().isConflict());
    }
}
