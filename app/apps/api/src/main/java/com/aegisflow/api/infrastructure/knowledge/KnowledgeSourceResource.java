package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSourceResource(
        UUID resourceId,
        UUID connectionId,
        String externalId,
        String resourceType,
        String title,
        String uri,
        String versionRef,
        String contentHash,
        String status,
        Instant lastSeenAt
) {
}
