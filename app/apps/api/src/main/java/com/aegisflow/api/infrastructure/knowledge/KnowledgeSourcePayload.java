package com.aegisflow.api.infrastructure.knowledge;

public record KnowledgeSourcePayload(
        String fileName,
        String mediaType,
        byte[] content,
        String versionRef
) {
}
