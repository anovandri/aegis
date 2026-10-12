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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    @PreAuthorize("@reviewAuthorization.canViewReviews(authentication)")
    List<HumanReview> listReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) ReviewType reviewType,
            @RequestParam(required = false) String assigneeRole
    ) {
        return reviewService.listReviews(Optional.ofNullable(status), Optional.ofNullable(reviewType), Optional.ofNullable(assigneeRole));
    }

    @GetMapping("/{reviewId}")
    @PreAuthorize("@reviewAuthorization.canViewReviews(authentication)")
    HumanReview getReview(@PathVariable UUID reviewId) {
        return reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
    }

    @GetMapping("/{reviewId}/decisions")
    @PreAuthorize("@reviewAuthorization.canParticipate(authentication, #reviewId)")
    List<HumanReviewDecision> decisions(@PathVariable UUID reviewId) {
        reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        return reviewService.decisions(reviewId);
    }

    @PostMapping("/{reviewId}/assign")
    @PreAuthorize("@reviewAuthorization.canAssign(authentication)")
    HumanReview assign(
            @PathVariable UUID reviewId,
            @Valid @RequestBody AssignReviewRequest request
    ) {
        try {
            return reviewService.assign(reviewId, request.assignee(), request.dueAt())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/bulk-assign")
    @PreAuthorize("@reviewAuthorization.canAssign(authentication)")
    List<HumanReview> bulkAssign(
            @Valid @RequestBody BulkAssignReviewRequest request
    ) {
        try {
            return reviewService.bulkAssign(request.reviewIds(), request.assignee(), request.dueAt());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/{reviewId}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@reviewAuthorization.canDecide(authentication, #reviewId)")
    HumanReviewDecision decide(
            @PathVariable UUID reviewId,
            Authentication authentication,
            @Valid @RequestBody SubmitReviewDecisionRequest request
    ) {
        try {
            HumanReview review = reviewService.findReview(reviewId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
            HumanReviewDecision decision = reviewService.decide(
                            reviewId,
                            request.decision(),
                            actorName(authentication, request.actor()),
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
    @PreAuthorize("@reviewAuthorization.canParticipate(authentication, #reviewId)")
    List<HumanReviewComment> comments(
            @PathVariable UUID reviewId
    ) {
        reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        return reviewService.comments(reviewId);
    }

    @PostMapping("/{reviewId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@reviewAuthorization.canParticipate(authentication, #reviewId)")
    HumanReviewComment addComment(
            @PathVariable UUID reviewId,
            Authentication authentication,
            @Valid @RequestBody AddReviewCommentRequest request
    ) {
        try {
            return reviewService.addComment(reviewId, actorName(authentication, request.actor()), request.body())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{reviewId}/attachments")
    @PreAuthorize("@reviewAuthorization.canParticipate(authentication, #reviewId)")
    List<HumanReviewAttachment> attachments(
            @PathVariable UUID reviewId
    ) {
        reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        return reviewService.attachments(reviewId);
    }

    @PostMapping("/{reviewId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@reviewAuthorization.canAttach(authentication, #reviewId)")
    HumanReviewAttachment addAttachment(
            @PathVariable UUID reviewId,
            Authentication authentication,
            @Valid @RequestBody AddReviewAttachmentRequest request
    ) {
        try {
            return reviewService.addAttachment(
                            reviewId,
                            actorName(authentication, request.actor()),
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
    @PreAuthorize("@reviewAuthorization.canParticipate(authentication, #reviewId)")
    List<HumanReviewTimelineItem> timeline(
            @PathVariable UUID reviewId
    ) {
        reviewService.findReview(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        return reviewService.timeline(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
    }

    private String actorName(Authentication authentication, String fallbackActor) {
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return authentication.getName();
        }
        return fallbackActor;
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
}
