package com.aegisflow.api.application;

import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.HumanReview;
import com.aegisflow.api.domain.HumanReviewDecision;
import com.aegisflow.api.domain.Project;
import com.aegisflow.api.domain.ProjectContext;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.infrastructure.review.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceTest {
    private final ProjectService projectService = mock(ProjectService.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final ReviewService reviewService = new ReviewService(
            Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC),
            projectService,
            reviewRepository
    );

    @Test
    void createTechnicalReviewCreatesReviewTransitionsProjectAndSummarizesArtifacts() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId, WorkflowState.SYSTEM_ANALYSIS);
        ArtifactVersion brd = artifact(ArtifactType.BRD, 1);
        ArtifactVersion architecture = artifact(ArtifactType.ARCHITECTURE_ANALYSIS, 2);
        when(reviewRepository.findOpenReview(projectId, ReviewType.TECHNICAL_REVIEW)).thenReturn(Optional.empty());
        when(projectService.findProject(projectId)).thenReturn(Optional.of(project));
        when(projectService.assembleContext(projectId)).thenReturn(Optional.of(new ProjectContext(
                projectId,
                project.name(),
                project.currentState(),
                List.of(architecture, brd),
                "mvp-default-policy",
                Instant.parse("2026-10-10T00:00:00Z")
        )));

        HumanReview result = reviewService.createTechnicalReview(projectId).orElseThrow();

        assertThat(result.reviewType()).isEqualTo(ReviewType.TECHNICAL_REVIEW);
        assertThat(result.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(result.workflowState()).isEqualTo(WorkflowState.HUMAN_TECHNICAL_REVIEW);
        assertThat(result.artifactSummary()).isEqualTo("ARCHITECTURE_ANALYSIS v2 / BRD v1");
        assertThat(result.dueAt()).isEqualTo(Instant.parse("2026-10-11T00:00:00Z"));
        assertThat(result.blockingQuestions()).hasSize(3);
        verify(projectService).transitionProject(projectId, WorkflowState.HUMAN_TECHNICAL_REVIEW);
        verify(reviewRepository).saveReview(result);
    }

    @Test
    void createTechnicalReviewReturnsExistingOpenReviewIdempotently() {
        UUID projectId = UUID.randomUUID();
        HumanReview openReview = review(projectId, ReviewType.TECHNICAL_REVIEW, ReviewStatus.PENDING);
        when(reviewRepository.findOpenReview(projectId, ReviewType.TECHNICAL_REVIEW)).thenReturn(Optional.of(openReview));

        HumanReview result = reviewService.createTechnicalReview(projectId).orElseThrow();

        assertThat(result).isEqualTo(openReview);
        verify(projectService, never()).transitionProject(any(), any());
        verify(reviewRepository, never()).saveReview(any());
    }

    @Test
    void createPmReviewCreatesMediumPriorityReviewWithEighteenHourSla() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId, WorkflowState.JIRA_DRAFT);
        when(reviewRepository.findOpenReview(projectId, ReviewType.PM_APPROVAL)).thenReturn(Optional.empty());
        when(projectService.findProject(projectId)).thenReturn(Optional.of(project));
        when(projectService.assembleContext(projectId)).thenReturn(Optional.of(new ProjectContext(
                projectId,
                project.name(),
                project.currentState(),
                List.of(artifact(ArtifactType.ESTIMATION, 1), artifact(ArtifactType.JIRA_PLAN, 1)),
                "mvp-default-policy",
                Instant.parse("2026-10-10T00:00:00Z")
        )));

        HumanReview result = reviewService.createPmReview(projectId).orElseThrow();

        assertThat(result.reviewType()).isEqualTo(ReviewType.PM_APPROVAL);
        assertThat(result.assigneeRole()).isEqualTo("Project Management");
        assertThat(result.priority()).isEqualTo("Medium");
        assertThat(result.dueAt()).isEqualTo(Instant.parse("2026-10-10T18:00:00Z"));
        assertThat(result.artifactSummary()).isEqualTo("ESTIMATION v1 / JIRA_PLAN v1");
        verify(projectService).transitionProject(projectId, WorkflowState.HUMAN_PM_APPROVAL);
        verify(reviewRepository).saveReview(result);
    }

    @Test
    void assignMovesOpenReviewToAssignedAndUpdatesReviewerFields() {
        UUID reviewId = UUID.randomUUID();
        HumanReview review = review(reviewId, UUID.randomUUID(), ReviewType.TECHNICAL_REVIEW, ReviewStatus.PENDING);
        Instant dueAt = Instant.parse("2026-10-12T00:00:00Z");
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review));

        HumanReview result = reviewService.assign(reviewId, "architect-001", dueAt).orElseThrow();

        assertThat(result.status()).isEqualTo(ReviewStatus.ASSIGNED);
        assertThat(result.assignee()).isEqualTo("architect-001");
        assertThat(result.dueAt()).isEqualTo(dueAt);
        assertThat(result.updatedAt()).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
        verify(reviewRepository).updateAssignment(reviewId, ReviewStatus.ASSIGNED, "architect-001", dueAt, Instant.parse("2026-10-10T00:00:00Z"));
    }

    @Test
    void cannotAssignDecidedReview() {
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review(UUID.randomUUID(), ReviewType.PM_APPROVAL, ReviewStatus.DECIDED)));

        assertThatThrownBy(() -> reviewService.assign(reviewId, "pm-001", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Decided reviews cannot be reassigned");
        verify(reviewRepository, never()).updateAssignment(any(), any(), any(), any(), any());
    }

    @Test
    void bulkAssignFailsWhenAnyReviewDoesNotExist() {
        UUID firstReviewId = UUID.randomUUID();
        UUID missingReviewId = UUID.randomUUID();
        HumanReview review = review(firstReviewId, UUID.randomUUID(), ReviewType.TECHNICAL_REVIEW, ReviewStatus.PENDING);
        when(reviewRepository.findReview(firstReviewId)).thenReturn(Optional.of(review));
        when(reviewRepository.findReview(missingReviewId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.bulkAssign(List.of(firstReviewId, missingReviewId), "review-lead", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Review not found");
        verify(reviewRepository).updateAssignment(eq(firstReviewId), eq(ReviewStatus.ASSIGNED), eq("review-lead"), eq(null), any());
    }

    @Test
    void rejectTechnicalReviewReturnsProjectToArchitectureAnalysis() {
        UUID projectId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        HumanReview review = review(reviewId, projectId, ReviewType.TECHNICAL_REVIEW, ReviewStatus.PENDING);
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review));
        when(projectService.findProject(projectId)).thenReturn(Optional.of(new Project(
                projectId,
                "Dynamic QRIS Payment",
                "Payments",
                "Merchant Payments",
                WorkflowState.HUMAN_TECHNICAL_REVIEW,
                Instant.parse("2026-10-10T00:00:00Z")
        )));

        var decision = reviewService.decide(
                reviewId,
                HumanDecision.REJECT,
                "user-architect-001",
                null,
                List.of(),
                "Insufficient service reuse evidence"
        ).orElseThrow();

        assertThat(decision.previousState()).isEqualTo(WorkflowState.HUMAN_TECHNICAL_REVIEW);
        assertThat(decision.nextState()).isEqualTo(WorkflowState.ARCHITECTURE_ANALYSIS);
        assertThat(decision.rejectionReason()).isEqualTo("Insufficient service reuse evidence");
        verify(projectService).transitionProject(projectId, WorkflowState.ARCHITECTURE_ANALYSIS);
        verify(reviewRepository).markDecided(reviewId, Instant.parse("2026-10-10T00:00:00Z"));
    }

    @Test
    void approvePmReviewMovesProjectToJiraCreatedAndStoresDecision() {
        UUID projectId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        HumanReview review = review(reviewId, projectId, ReviewType.PM_APPROVAL, ReviewStatus.ASSIGNED);
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review));
        when(projectService.findProject(projectId)).thenReturn(Optional.of(project(projectId, WorkflowState.HUMAN_PM_APPROVAL)));

        HumanReviewDecision decision = reviewService.decide(
                reviewId,
                HumanDecision.APPROVE,
                "pm-001",
                null,
                List.of(),
                null
        ).orElseThrow();

        assertThat(decision.decision()).isEqualTo(HumanDecision.APPROVE);
        assertThat(decision.previousState()).isEqualTo(WorkflowState.HUMAN_PM_APPROVAL);
        assertThat(decision.nextState()).isEqualTo(WorkflowState.JIRA_CREATED);
        assertThat(decision.generatedArtifactId()).isNull();
        verify(projectService).transitionProject(projectId, WorkflowState.JIRA_CREATED);
        verify(reviewRepository).saveDecision(decision);
        verify(reviewRepository).markDecided(reviewId, Instant.parse("2026-10-10T00:00:00Z"));
    }

    @Test
    void approveWithChangeGeneratesCorrectionArtifactBeforeAdvancingState() {
        UUID projectId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        HumanReview review = review(reviewId, projectId, ReviewType.TECHNICAL_REVIEW, ReviewStatus.PENDING);
        ArtifactVersion generatedArtifact = new ArtifactVersion(
                artifactId,
                ArtifactType.ARCHITECTURE_ANALYSIS,
                3,
                "human-review-correction-%s.md".formatted(reviewId),
                "text/markdown",
                42,
                "hash",
                "bucket",
                "object",
                null,
                Instant.parse("2026-10-10T00:00:00Z")
        );
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review));
        when(projectService.findProject(projectId)).thenReturn(Optional.of(project(projectId, WorkflowState.HUMAN_TECHNICAL_REVIEW)));
        when(projectService.addGeneratedArtifact(eq(projectId), eq(ArtifactType.ARCHITECTURE_ANALYSIS), any(), eq("text/markdown"), any()))
                .thenReturn(Optional.of(generatedArtifact));

        HumanReviewDecision decision = reviewService.decide(
                reviewId,
                HumanDecision.APPROVE_WITH_CHANGE,
                "architect-001",
                "Reuse the existing payment orchestration service.",
                List.of(),
                null
        ).orElseThrow();

        assertThat(decision.nextState()).isEqualTo(WorkflowState.ESTIMATION);
        assertThat(decision.generatedArtifactId()).isEqualTo(artifactId);
        assertThat(decision.generatedArtifactVersion()).isEqualTo(3);
        verify(projectService).transitionProject(projectId, WorkflowState.ESTIMATION);
        verify(reviewRepository).saveDecision(decision);
    }

    @Test
    void requestClarificationKeepsReviewStateAndStoresQuestions() {
        UUID projectId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        HumanReview review = review(reviewId, projectId, ReviewType.TECHNICAL_REVIEW, ReviewStatus.PENDING);
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review));
        when(projectService.findProject(projectId)).thenReturn(Optional.of(project(projectId, WorkflowState.HUMAN_TECHNICAL_REVIEW)));

        HumanReviewDecision decision = reviewService.decide(
                reviewId,
                HumanDecision.REQUEST_CLARIFICATION,
                "architect-001",
                null,
                List.of("What is peak TPS?", "What is the timeout policy?"),
                null
        ).orElseThrow();

        assertThat(decision.nextState()).isEqualTo(WorkflowState.HUMAN_TECHNICAL_REVIEW);
        assertThat(decision.clarificationQuestions()).containsExactly("What is peak TPS?", "What is the timeout policy?");
        verify(projectService).transitionProject(projectId, WorkflowState.HUMAN_TECHNICAL_REVIEW);
        verify(reviewRepository).saveDecision(decision);
    }

    @Test
    void approveWithChangeRequiresCorrectionBeforeLoadingProject() {
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review(UUID.randomUUID(), ReviewType.TECHNICAL_REVIEW, ReviewStatus.PENDING)));

        assertThatThrownBy(() -> reviewService.decide(
                reviewId,
                HumanDecision.APPROVE_WITH_CHANGE,
                "user-architect-001",
                null,
                List.of(),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Correction is required");
        verify(projectService, never()).findProject(any());
    }

    @Test
    void requestClarificationRequiresQuestionsBeforeLoadingProject() {
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review(UUID.randomUUID(), ReviewType.TECHNICAL_REVIEW, ReviewStatus.PENDING)));

        assertThatThrownBy(() -> reviewService.decide(
                reviewId,
                HumanDecision.REQUEST_CLARIFICATION,
                "architect-001",
                null,
                List.of(),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Clarification questions are required");
        verify(projectService, never()).findProject(any());
    }

    @Test
    void rejectRequiresReasonBeforeLoadingProject() {
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review(UUID.randomUUID(), ReviewType.PM_APPROVAL, ReviewStatus.PENDING)));

        assertThatThrownBy(() -> reviewService.decide(
                reviewId,
                HumanDecision.REJECT,
                "pm-001",
                null,
                List.of(),
                " "
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rejection reason is required");
        verify(projectService, never()).findProject(any());
    }

    @Test
    void cannotDecideReviewTwice() {
        UUID reviewId = UUID.randomUUID();
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review(UUID.randomUUID(), ReviewType.TECHNICAL_REVIEW, ReviewStatus.DECIDED)));

        assertThatThrownBy(() -> reviewService.decide(
                reviewId,
                HumanDecision.APPROVE,
                "architect-001",
                null,
                List.of(),
                null
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Review has already been decided");
        verify(projectService, never()).findProject(any());
    }

    @Test
    void savedDecisionForRejectNormalizesBlankOptionalFields() {
        UUID projectId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        HumanReview review = review(reviewId, projectId, ReviewType.PM_APPROVAL, ReviewStatus.PENDING);
        when(reviewRepository.findReview(reviewId)).thenReturn(Optional.of(review));
        when(projectService.findProject(projectId)).thenReturn(Optional.of(project(projectId, WorkflowState.HUMAN_PM_APPROVAL)));
        ArgumentCaptor<HumanReviewDecision> captor = ArgumentCaptor.forClass(HumanReviewDecision.class);

        reviewService.decide(
                reviewId,
                HumanDecision.REJECT,
                "pm-001",
                " ",
                null,
                "Capacity is not available this sprint"
        ).orElseThrow();

        verify(reviewRepository).saveDecision(captor.capture());
        assertThat(captor.getValue().correction()).isNull();
        assertThat(captor.getValue().clarificationQuestions()).isEmpty();
        assertThat(captor.getValue().rejectionReason()).isEqualTo("Capacity is not available this sprint");
        assertThat(captor.getValue().nextState()).isEqualTo(WorkflowState.JIRA_DRAFT);
    }

    private Project project(UUID projectId, WorkflowState state) {
        return new Project(
                projectId,
                "Dynamic QRIS Payment",
                "Payments",
                "Merchant Payments",
                state,
                Instant.parse("2026-10-10T00:00:00Z")
        );
    }

    private ArtifactVersion artifact(ArtifactType artifactType, int version) {
        return new ArtifactVersion(
                UUID.randomUUID(),
                artifactType,
                version,
                "%s-v%d.md".formatted(artifactType.name().toLowerCase(), version),
                "text/markdown",
                10,
                "hash-%s-%d".formatted(artifactType.name(), version),
                "bucket",
                "object/%s/%d".formatted(artifactType.name(), version),
                null,
                Instant.parse("2026-10-10T00:00:00Z")
        );
    }

    private HumanReview review(UUID projectId, ReviewType reviewType, ReviewStatus status) {
        return review(UUID.randomUUID(), projectId, reviewType, status);
    }

    private HumanReview review(UUID reviewId, UUID projectId, ReviewType reviewType, ReviewStatus status) {
        return new HumanReview(
                reviewId,
                projectId,
                "Dynamic QRIS Payment",
                reviewType,
                status,
                reviewType == ReviewType.PM_APPROVAL ? WorkflowState.HUMAN_PM_APPROVAL : WorkflowState.HUMAN_TECHNICAL_REVIEW,
                "Technical review",
                "Review summary",
                "Digital Architecture",
                null,
                "High",
                Instant.parse("2026-10-11T00:00:00Z"),
                "BRD v1",
                List.of("Blocking question"),
                Instant.parse("2026-10-10T00:00:00Z"),
                Instant.parse("2026-10-10T00:00:00Z")
        );
    }
}
