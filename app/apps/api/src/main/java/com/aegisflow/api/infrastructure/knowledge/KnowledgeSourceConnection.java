package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSourceConnection(
        UUID connectionId,
        String sourceId,
        String adapterType,
        String connectionName,
        String connectionStatus,
        String resourceLocator,
        String authType,
        String credentialRef,
        String configJson,
        Instant lastCheckedAt,
        String lastError,
        Instant createdAt
) {
}
