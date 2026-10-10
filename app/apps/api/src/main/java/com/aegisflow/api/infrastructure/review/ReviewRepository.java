package com.aegisflow.api.infrastructure.review;

import com.aegisflow.api.domain.HumanReview;
import com.aegisflow.api.domain.HumanReviewDecision;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {
    void saveReview(HumanReview review);

    Optional<HumanReview> findReview(UUID reviewId);

    Optional<HumanReview> findOpenReview(UUID projectId, ReviewType reviewType);

    List<HumanReview> findReviews(Optional<ReviewStatus> status, Optional<ReviewType> reviewType, Optional<String> assigneeRole);

    void updateAssignment(UUID reviewId, ReviewStatus status, String assignee, java.time.Instant dueAt, java.time.Instant updatedAt);

    void markDecided(UUID reviewId, java.time.Instant updatedAt);

    void saveDecision(HumanReviewDecision decision);

    List<HumanReviewDecision> findDecisions(UUID reviewId);
}
