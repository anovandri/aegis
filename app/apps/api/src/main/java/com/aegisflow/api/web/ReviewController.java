package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectLifecycleStarter;
import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.HumanReview;
import com.aegisflow.api.domain.HumanReviewAttachment;
import com.aegisflow.api.domain.HumanReviewComment;
import com.aegisflow.api.domain.HumanReviewDecision;
import com.aegisflow.api.domain.HumanReviewTimelineItem;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.workflow.ReviewDecisionSignal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;
    private final ProjectLifecycleStarter projectLifecycleStarter;

    public ReviewController(ReviewService reviewService, ProjectLifecycleStarter projectLifecycleStarter) {
        this.reviewService = reviewService;
        this.projectLifecycleStarter = projectLifecycleStarter;
    }

    @GetMapping
    List<HumanReview> listReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) ReviewType reviewType,
            @RequestParam(required = false) String assigneeRole
    ) {
        return reviewService.listReviews(Optional.ofNullable(status), Optional.ofNullable(reviewType), Optional.ofNullable(assigneeRole));
    }

    @GetMapping("/{reviewId}")
    HumanReview getReview(@PathVariable UUID reviewId) {
        return reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
    }

    @GetMapping("/{reviewId}/decisions")
    List<HumanReviewDecision> decisions(@PathVariable UUID reviewId) {
        reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        return reviewService.decisions(reviewId);
    }

    @PostMapping("/{reviewId}/assign")
    HumanReview assign(
            @PathVariable UUID reviewId,
            @RequestHeader(name = "X-Aegis-Roles", required = false) String rolesHeader,
            @Valid @RequestBody AssignReviewRequest request
    ) {
        try {
            authorizeAssignment(actor(rolesHeader));
            return reviewService.assign(reviewId, request.assignee(), request.dueAt())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/bulk-assign")
    List<HumanReview> bulkAssign(
            @RequestHeader(name = "X-Aegis-Roles", required = false) String rolesHeader,
            @Valid @RequestBody BulkAssignReviewRequest request
    ) {
        try {
            authorizeAssignment(actor(rolesHeader));
            return reviewService.bulkAssign(request.reviewIds(), request.assignee(), request.dueAt());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/{reviewId}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    HumanReviewDecision decide(
            @PathVariable UUID reviewId,
            @RequestHeader(name = "X-Aegis-Roles", required = false) String rolesHeader,
            @Valid @RequestBody SubmitReviewDecisionRequest request
    ) {
        try {
            HumanReview review = reviewService.findReview(reviewId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
            authorizeDecision(actor(rolesHeader), review.reviewType());
            HumanReviewDecision decision = reviewService.decide(
                            reviewId,
                            request.decision(),
                            request.actor(),
                            request.correction(),
                            request.clarificationQuestions(),
                            request.rejectionReason()
                    )
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
            projectLifecycleStarter.signalReviewDecision(
                    decision.projectId(),
                    new ReviewDecisionSignal(
                            decision.projectId(),
                            decision.reviewId(),
                            review.reviewType(),
                            decision.decision(),
                            decision.nextState(),
                            decision.actor()
                    )
            );
            return decision;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{reviewId}/comments")
    List<HumanReviewComment> comments(@PathVariable UUID reviewId) {
        reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        return reviewService.comments(reviewId);
    }

    @PostMapping("/{reviewId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    HumanReviewComment addComment(@PathVariable UUID reviewId, @Valid @RequestBody AddReviewCommentRequest request) {
        try {
            return reviewService.addComment(reviewId, request.actor(), request.body())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{reviewId}/attachments")
    List<HumanReviewAttachment> attachments(@PathVariable UUID reviewId) {
        reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        return reviewService.attachments(reviewId);
    }

    @PostMapping("/{reviewId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    HumanReviewAttachment addAttachment(@PathVariable UUID reviewId, @Valid @RequestBody AddReviewAttachmentRequest request) {
        try {
            return reviewService.addAttachment(
                            reviewId,
                            request.actor(),
                            request.fileName(),
                            request.mediaType(),
                            request.storageUri(),
                            request.description()
                    )
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{reviewId}/timeline")
    List<HumanReviewTimelineItem> timeline(@PathVariable UUID reviewId) {
        return reviewService.timeline(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
    }

    private ReviewActor actor(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return new ReviewActor(List.of("ADMIN"));
        }
        return new ReviewActor(Arrays.stream(rolesHeader.split(","))
                .map(String::strip)
                .filter(role -> !role.isBlank())
                .map(String::toUpperCase)
                .toList());
    }

    private void authorizeAssignment(ReviewActor actor) {
        if (!actor.hasAnyRole("ADMIN", "TECHNICAL_REVIEWER", "PM_APPROVER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to assign reviews");
        }
    }

    private void authorizeDecision(ReviewActor actor, ReviewType reviewType) {
        if (actor.hasRole("ADMIN")) {
            return;
        }
        boolean allowed = switch (reviewType) {
            case TECHNICAL_REVIEW -> actor.hasRole("TECHNICAL_REVIEWER");
            case PM_APPROVAL -> actor.hasRole("PM_APPROVER");
            case CLARIFICATION -> actor.hasAnyRole("BUSINESS_USER", "TECHNICAL_REVIEWER", "PM_APPROVER");
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Actor is not allowed to decide this review type");
        }
    }

    record AssignReviewRequest(
            @NotBlank String assignee,
            Instant dueAt
    ) {
    }

    record BulkAssignReviewRequest(
            @NotEmpty List<UUID> reviewIds,
            @NotBlank String assignee,
            Instant dueAt
    ) {
    }

    record SubmitReviewDecisionRequest(
            @NotNull HumanDecision decision,
            @NotBlank String actor,
            String correction,
            List<String> clarificationQuestions,
            String rejectionReason
    ) {
        SubmitReviewDecisionRequest {
            clarificationQuestions = clarificationQuestions == null ? List.of() : List.copyOf(clarificationQuestions);
        }
    }

    record AddReviewCommentRequest(
            @NotBlank String actor,
            @NotBlank String body
    ) {
    }

    record AddReviewAttachmentRequest(
            @NotBlank String actor,
            @NotBlank String fileName,
            @NotBlank String mediaType,
            @NotBlank String storageUri,
            String description
    ) {
    }

    private record ReviewActor(List<String> roles) {
        boolean hasRole(String role) {
            return roles.contains(role);
        }

        boolean hasAnyRole(String... allowedRoles) {
            return Arrays.stream(allowedRoles).anyMatch(this::hasRole);
        }
    }
}
