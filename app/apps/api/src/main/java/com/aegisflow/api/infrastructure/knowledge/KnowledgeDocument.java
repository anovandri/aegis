package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeDocument(
        UUID documentId,
        String sourceId,
        String sourceTitle,
        String sourceType,
        String authority,
        List<String> allowedAgents,
        List<String> workflowStates,
        List<String> tags,
        int latestVersion,
        Instant createdAt,
        List<KnowledgeDocumentVersion> versions
) {
}
