package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;

public record KnowledgeSeedDocument(
        String sourceId,
        String sourceTitle,
        String sourceType,
        String authority,
        List<String> allowedAgents,
        List<String> workflowStates,
        List<String> tags,
        String excerpt
) {
}
