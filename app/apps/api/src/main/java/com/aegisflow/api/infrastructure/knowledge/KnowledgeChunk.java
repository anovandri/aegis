package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;
import java.util.UUID;

public record KnowledgeChunk(
        UUID chunkId,
        UUID documentId,
        String sourceId,
        String sourceTitle,
        String sourceType,
        String authority,
        List<String> allowedAgents,
        List<String> workflowStates,
        List<String> tags,
        int documentVersion,
        int chunkIndex,
        String text,
        String embeddingModel,
        List<Double> embedding
) {
}
