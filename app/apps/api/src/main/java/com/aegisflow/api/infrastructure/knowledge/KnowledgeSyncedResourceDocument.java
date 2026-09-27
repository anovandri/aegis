package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSyncedResourceDocument(
        String resourceKey,
        String sourceId,
        UUID connectionId,
        String externalId,
        UUID documentId,
        String lastContentHash,
        Instant updatedAt
) {
}
