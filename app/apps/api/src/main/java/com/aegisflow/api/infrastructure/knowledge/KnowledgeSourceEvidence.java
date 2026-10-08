package com.aegisflow.api.infrastructure.knowledge;

public record KnowledgeSourceEvidence(
        String title,
        String detail,
        String tag,
        String sourceTitle,
        int documentVersion,
        int chunkIndex
) {
}
