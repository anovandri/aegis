package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.UUID;

public record Project(
        UUID projectId,
        String name,
        String businessOwner,
        String domain,
        WorkflowState currentState,
        Instant createdAt
) {
}
