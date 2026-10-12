package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HumanReviewComment(
        UUID commentId,
        UUID reviewId,
        UUID projectId,
        String actor,
        String body,
        List<String> mentions,
        Instant createdAt
) {
    public HumanReviewComment {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
    }
}
