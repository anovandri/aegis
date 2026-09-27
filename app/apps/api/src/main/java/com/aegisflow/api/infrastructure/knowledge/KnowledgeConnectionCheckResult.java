package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;

public record KnowledgeConnectionCheckResult(
        String status,
        String message,
        Instant checkedAt
) {
}
