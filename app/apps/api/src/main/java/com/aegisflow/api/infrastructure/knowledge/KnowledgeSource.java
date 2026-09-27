package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;
import java.util.List;

public record KnowledgeSource(
        String sourceId,
        String name,
        String sourceType,
        String authority,
        String status,
        String ownerTeam,
        boolean enabled,
        int freshnessSlaHours,
        String syncMode,
        String sensitivityPolicy,
        List<String> allowedAgents,
        List<String> workflowStates,
        List<String> tags,
        Instant lastSyncedAt,
        Instant reviewDueAt,
        Instant createdAt,
        long documentCount,
        long chunkCount,
        long agentUseCount
) {
}
