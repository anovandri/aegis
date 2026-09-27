package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeAuditEvent(
        UUID eventId,
        String sourceId,
        String action,
        String actor,
        String details,
        Instant createdAt
) {
}
