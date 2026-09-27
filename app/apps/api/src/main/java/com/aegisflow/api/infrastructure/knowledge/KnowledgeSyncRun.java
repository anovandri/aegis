package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeSyncRun(
        UUID syncRunId,
        String sourceId,
        String status,
        Instant startedAt,
        Instant completedAt,
        int recordsChanged,
        String errorMessage
) {
}
