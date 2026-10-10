package com.aegisflow.api.web;

import com.aegisflow.api.application.ReviewService;
import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.HumanReview;
import com.aegisflow.api.domain.HumanReviewDecision;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
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

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
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
    HumanReview assign(@PathVariable UUID reviewId, @Valid @RequestBody AssignReviewRequest request) {
        try {
            return reviewService.assign(reviewId, request.assignee(), request.dueAt())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/bulk-assign")
    List<HumanReview> bulkAssign(@Valid @RequestBody BulkAssignReviewRequest request) {
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
    HumanReviewDecision decide(@PathVariable UUID reviewId, @Valid @RequestBody SubmitReviewDecisionRequest request) {
        try {
            return reviewService.decide(
                            reviewId,
                            request.decision(),
                            request.actor(),
                            request.correction(),
                            request.clarificationQuestions(),
                            request.rejectionReason()
                    )
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
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
}
