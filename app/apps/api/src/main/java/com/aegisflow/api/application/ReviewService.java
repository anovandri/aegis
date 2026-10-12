package com.aegisflow.api.application;

import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.HumanReview;
import com.aegisflow.api.domain.HumanReviewAttachment;
import com.aegisflow.api.domain.HumanReviewComment;
import com.aegisflow.api.domain.HumanReviewDecision;
import com.aegisflow.api.domain.HumanReviewTimelineItem;
import com.aegisflow.api.domain.Project;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.infrastructure.review.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class ReviewService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9._-]+)");

    private final Clock clock;
    private final ProjectService projectService;
    private final ReviewRepository reviewRepository;

    public ReviewService(Clock clock, ProjectService projectService, ReviewRepository reviewRepository) {
        this.clock = clock;
        this.projectService = projectService;
        this.reviewRepository = reviewRepository;
    }

    public List<HumanReview> listReviews(Optional<ReviewStatus> status, Optional<ReviewType> reviewType, Optional<String> assigneeRole) {
        return reviewRepository.findReviews(status, reviewType, assigneeRole);
    }

    public Optional<HumanReview> findReview(UUID reviewId) {
        return reviewRepository.findReview(reviewId);
    }

    public List<HumanReviewDecision> decisions(UUID reviewId) {
        return reviewRepository.findDecisions(reviewId);
    }

    public List<HumanReviewComment> comments(UUID reviewId) {
        return reviewRepository.findComments(reviewId);
    }

    public List<HumanReviewAttachment> attachments(UUID reviewId) {
        return reviewRepository.findAttachments(reviewId);
    }

    public Optional<List<HumanReviewTimelineItem>> timeline(UUID reviewId) {
        return reviewRepository.findReview(reviewId)
                .map(review -> Stream.concat(
                                Stream.of(new HumanReviewTimelineItem(
                                        review.reviewId(),
                                        review.reviewId(),
                                        review.projectId(),
                                        "REVIEW_CREATED",
                                        "system",
                                        "%s review created".formatted(review.reviewType().name()),
                                        "status=%s;workflowState=%s".formatted(review.status().name(), review.workflowState().name()),
                                        review.createdAt()
                                )),
                                reviewRepository.findEvents(reviewId).stream()
                        )
                        .sorted(java.util.Comparator.comparing(HumanReviewTimelineItem::createdAt))
                        .toList());
    }

    @Transactional
    public Optional<HumanReview> createTechnicalReview(UUID projectId) {
        return createReview(
                projectId,
                ReviewType.TECHNICAL_REVIEW,
                WorkflowState.HUMAN_TECHNICAL_REVIEW,
                "Technical review",
                "Architecture and system analysis are ready for Digital Architecture and System Analyst review.",
                "Digital Architecture",
                "High",
                List.of(
                        "Expected TPS, peak TPS, and issuer callback timeout must be confirmed.",
                        "Architecture reuse recommendation requires human approval.",
                        "Correction creates a new artifact version and evaluation record."
                )
        );
    }

    @Transactional
    public Optional<HumanReview> createPmReview(UUID projectId) {
        return createReview(
                projectId,
                ReviewType.PM_APPROVAL,
                WorkflowState.HUMAN_PM_APPROVAL,
                "PM approval",
                "Estimation and Jira draft are ready for project management approval.",
                "Project Management",
                "Medium",
                List.of(
                        "Validate implementation estimate and capacity assumptions.",
                        "Confirm Jira epic/story/task structure before external ticket creation."
                )
        );
    }

    @Transactional
    public Optional<HumanReview> assign(UUID reviewId, String assignee, Instant dueAt) {
        return reviewRepository.findReview(reviewId)
                .map(review -> {
                    if (review.status() == ReviewStatus.DECIDED) {
                        throw new IllegalStateException("Decided reviews cannot be reassigned");
                    }
                    Instant now = Instant.now(clock);
                    reviewRepository.updateAssignment(reviewId, ReviewStatus.ASSIGNED, assignee, dueAt, now);
                    saveEvent(
                            review,
                            "REVIEW_ASSIGNED",
                            assignee,
                            "Review assigned to %s".formatted(assignee),
                            "dueAt=%s".formatted(dueAt == null ? "" : dueAt)
                    );
                    return new HumanReview(
                            review.reviewId(),
                            review.projectId(),
                            review.projectName(),
                            review.reviewType(),
                            ReviewStatus.ASSIGNED,
                            review.workflowState(),
                            review.title(),
                            review.summary(),
                            review.assigneeRole(),
                            assignee,
                            review.priority(),
                            dueAt,
                            review.artifactSummary(),
                            review.blockingQuestions(),
                            review.createdAt(),
                            now
                    );
                });
    }

    @Transactional
    public List<HumanReview> bulkAssign(List<UUID> reviewIds, String assignee, Instant dueAt) {
        return reviewIds.stream()
                .map(reviewId -> assign(reviewId, assignee, dueAt)
                        .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId)))
                .toList();
    }

    @Transactional
    public Optional<HumanReviewComment> addComment(UUID reviewId, String actor, String body) {
        return reviewRepository.findReview(reviewId)
                .map(review -> {
                    String normalizedBody = requireText(body, "Comment body is required");
                    Instant now = Instant.now(clock);
                    HumanReviewComment comment = new HumanReviewComment(
                            UUID.randomUUID(),
                            review.reviewId(),
                            review.projectId(),
                            requireText(actor, "Actor is required"),
                            normalizedBody,
                            extractMentions(normalizedBody),
                            now
                    );
                    reviewRepository.saveComment(comment);
                    saveEvent(
                            review,
                            "COMMENT_ADDED",
                            comment.actor(),
                            "Comment added",
                            "commentId=%s;mentions=%s".formatted(comment.commentId(), String.join(",", comment.mentions()))
                    );
                    return comment;
                });
    }

    @Transactional
    public Optional<HumanReviewAttachment> addAttachment(
            UUID reviewId,
            String actor,
            String fileName,
            String mediaType,
            String storageUri,
            String description
    ) {
        return reviewRepository.findReview(reviewId)
                .map(review -> {
                    Instant now = Instant.now(clock);
                    HumanReviewAttachment attachment = new HumanReviewAttachment(
                            UUID.randomUUID(),
                            review.reviewId(),
                            review.projectId(),
                            requireText(actor, "Actor is required"),
                            requireText(fileName, "File name is required"),
                            requireText(mediaType, "Media type is required"),
                            requireText(storageUri, "Storage URI is required"),
                            blankToNull(description),
                            now
                    );
                    reviewRepository.saveAttachment(attachment);
                    saveEvent(
                            review,
                            "ATTACHMENT_ADDED",
                            attachment.actor(),
                            "Attachment added: %s".formatted(attachment.fileName()),
                            "attachmentId=%s;mediaType=%s;storageUri=%s".formatted(
                                    attachment.attachmentId(),
                                    attachment.mediaType(),
                                    attachment.storageUri()
                            )
                    );
                    return attachment;
                });
    }

    @Transactional
    public Optional<HumanReviewDecision> decide(
            UUID reviewId,
            HumanDecision decision,
            String actor,
            String correction,
            List<String> clarificationQuestions,
            String rejectionReason
    ) {
        return reviewRepository.findReview(reviewId)
                .map(review -> {
                    if (review.status() == ReviewStatus.DECIDED) {
                        throw new IllegalStateException("Review has already been decided");
                    }
                    validateDecision(decision, correction, clarificationQuestions, rejectionReason);
                    Project project = projectService.findProject(review.projectId())
                            .orElseThrow(() -> new IllegalStateException("Project not found for review: " + review.projectId()));
                    WorkflowState previousState = project.currentState();
                    ArtifactVersion generatedArtifact = generatedCorrectionArtifact(review, decision, actor, correction);
                    WorkflowState nextState = nextState(review, decision);
                    projectService.transitionProject(review.projectId(), nextState);
                    Instant now = Instant.now(clock);
                    HumanReviewDecision reviewDecision = new HumanReviewDecision(
                            UUID.randomUUID(),
                            review.reviewId(),
                            review.projectId(),
                            decision,
                            actor,
                            blankToNull(correction),
                            clarificationQuestions == null ? List.of() : List.copyOf(clarificationQuestions),
                            blankToNull(rejectionReason),
                            previousState,
                            nextState,
                            generatedArtifact == null ? null : generatedArtifact.artifactId(),
                            generatedArtifact == null ? null : generatedArtifact.version(),
                            now
                    );
                    reviewRepository.saveDecision(reviewDecision);
                    reviewRepository.markDecided(reviewId, now);
                    saveEvent(
                            review,
                            "REVIEW_DECIDED",
                            actor,
                            "Review decision submitted: %s".formatted(decision.name()),
                            "decisionId=%s;previousState=%s;nextState=%s".formatted(
                                    reviewDecision.decisionId(),
                                    previousState.name(),
                                    nextState.name()
                            )
                    );
                    return reviewDecision;
                });
    }

    private Optional<HumanReview> createReview(
            UUID projectId,
            ReviewType reviewType,
            WorkflowState reviewState,
            String title,
            String summary,
            String assigneeRole,
            String priority,
            List<String> blockingQuestions
    ) {
        Optional<HumanReview> openReview = reviewRepository.findOpenReview(projectId, reviewType);
        if (openReview.isPresent()) {
            return openReview;
        }
        return projectService.findProject(projectId)
                .map(project -> {
                    Instant now = Instant.now(clock);
                    projectService.transitionProject(projectId, reviewState);
                    HumanReview review = new HumanReview(
                            UUID.randomUUID(),
                            project.projectId(),
                            project.name(),
                            reviewType,
                            ReviewStatus.PENDING,
                            reviewState,
                            title,
                            summary,
                            assigneeRole,
                            null,
                            priority,
                            now.plus(defaultSla(reviewType)),
                            artifactSummary(projectId),
                            blockingQuestions,
                            now,
                            now
                    );
                    reviewRepository.saveReview(review);
                    return review;
                });
    }

    private Duration defaultSla(ReviewType reviewType) {
        return switch (reviewType) {
            case TECHNICAL_REVIEW -> Duration.ofHours(24);
            case PM_APPROVAL -> Duration.ofHours(18);
            case CLARIFICATION -> Duration.ofHours(48);
        };
    }

    private String artifactSummary(UUID projectId) {
        return projectService.assembleContext(projectId)
                .map(context -> context.artifacts().stream()
                        .map(artifact -> "%s v%d".formatted(artifact.artifactType().name(), artifact.version()))
                        .sorted()
                        .reduce((left, right) -> left + " / " + right)
                        .orElse("No artifacts available"))
                .orElse("No artifacts available");
    }

    private void validateDecision(HumanDecision decision, String correction, List<String> clarificationQuestions, String rejectionReason) {
        if (decision == HumanDecision.APPROVE_WITH_CHANGE && (correction == null || correction.isBlank())) {
            throw new IllegalArgumentException("Correction is required for APPROVE_WITH_CHANGE");
        }
        if (decision == HumanDecision.REQUEST_CLARIFICATION && (clarificationQuestions == null || clarificationQuestions.isEmpty())) {
            throw new IllegalArgumentException("Clarification questions are required for REQUEST_CLARIFICATION");
        }
        if (decision == HumanDecision.REJECT && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required for REJECT");
        }
    }

    private ArtifactVersion generatedCorrectionArtifact(HumanReview review, HumanDecision decision, String actor, String correction) {
        if (decision != HumanDecision.APPROVE_WITH_CHANGE) {
            return null;
        }
        ArtifactType artifactType = correctionArtifactType(review.reviewType());
        String content = """
                # Human Review Correction

                Review: %s
                Actor: %s
                Decision: %s
                Target artifact: %s

                %s
                """.formatted(
                review.reviewId(),
                actor,
                decision.name(),
                artifactType.name(),
                correction
        );
        return projectService.addGeneratedArtifact(
                        review.projectId(),
                        artifactType,
                        "human-review-correction-%s.md".formatted(review.reviewId()),
                        "text/markdown",
                        content.getBytes(StandardCharsets.UTF_8)
                )
                .orElseThrow(() -> new IllegalStateException("Project not found for correction artifact: " + review.projectId()));
    }

    private ArtifactType correctionArtifactType(ReviewType reviewType) {
        return switch (reviewType) {
            case TECHNICAL_REVIEW -> ArtifactType.ARCHITECTURE_ANALYSIS;
            case PM_APPROVAL -> ArtifactType.JIRA_PLAN;
            case CLARIFICATION -> ArtifactType.REQUIREMENT_ANALYSIS;
        };
    }

    private WorkflowState nextState(HumanReview review, HumanDecision decision) {
        return switch (decision) {
            case APPROVE, APPROVE_WITH_CHANGE -> approvedNextState(review.reviewType());
            case REJECT -> rejectedNextState(review.reviewType());
            case REQUEST_CLARIFICATION -> review.workflowState();
        };
    }

    private WorkflowState approvedNextState(ReviewType reviewType) {
        return switch (reviewType) {
            case TECHNICAL_REVIEW -> WorkflowState.ESTIMATION;
            case PM_APPROVAL -> WorkflowState.JIRA_CREATED;
            case CLARIFICATION -> WorkflowState.REQUIREMENT_ANALYSIS;
        };
    }

    private WorkflowState rejectedNextState(ReviewType reviewType) {
        return switch (reviewType) {
            case TECHNICAL_REVIEW -> WorkflowState.ARCHITECTURE_ANALYSIS;
            case PM_APPROVAL -> WorkflowState.JIRA_DRAFT;
            case CLARIFICATION -> WorkflowState.REQUIREMENT_ANALYSIS;
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> extractMentions(String body) {
        return MENTION_PATTERN.matcher(body)
                .results()
                .map(matchResult -> matchResult.group(1).replaceAll("[.,;:!?)]*$", ""))
                .filter(mention -> !mention.isBlank())
                .distinct()
                .toList();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private void saveEvent(HumanReview review, String eventType, String actor, String message, String metadata) {
        reviewRepository.saveEvent(new HumanReviewTimelineItem(
                UUID.randomUUID(),
                review.reviewId(),
                review.projectId(),
                eventType,
                actor,
                message,
                metadata == null ? "" : metadata,
                Instant.now(clock)
        ));
    }
}
