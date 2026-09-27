package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectContext(
        UUID projectId,
        String name,
        WorkflowState currentState,
        List<ArtifactVersion> artifacts,
        String retrievalPolicyId,
        Instant assembledAt
) {
}
