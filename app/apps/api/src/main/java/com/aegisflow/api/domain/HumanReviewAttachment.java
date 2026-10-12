package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.UUID;

public record HumanReviewAttachment(
        UUID attachmentId,
        UUID reviewId,
        UUID projectId,
        String actor,
        String fileName,
        String mediaType,
        String storageUri,
        String description,
        Instant createdAt
) {
}
