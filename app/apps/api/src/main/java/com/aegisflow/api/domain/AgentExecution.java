package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.UUID;

public record AgentExecution(
        UUID executionId,
        UUID projectId,
        WorkflowState workflowState,
        String agentName,
        String agentVersion,
        String promptVersion,
        String model,
        AgentExecutionStatus status,
        double confidence,
        long inputTokens,
        long outputTokens,
        Instant startedAt,
        Instant completedAt,
        String summary
) {
}
