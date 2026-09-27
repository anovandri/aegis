package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;

public record KnowledgeDocumentVersion(
        int version,
        String fileName,
        String mediaType,
        long sizeBytes,
        String contentHash,
        String storageBucket,
        String storageObjectKey,
        String storageVersionId,
        String extractedText,
        Instant createdAt
) {
}
