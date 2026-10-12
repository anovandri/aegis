package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.UUID;

public record HumanReviewTimelineItem(
        UUID eventId,
        UUID reviewId,
        UUID projectId,
        String eventType,
        String actor,
        String message,
        String metadata,
        Instant createdAt
) {
}
