package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.UUID;

public record ArtifactVersion(
        UUID artifactId,
        ArtifactType artifactType,
        int version,
        String fileName,
        String mediaType,
        long sizeBytes,
        String contentHash,
        String storageBucket,
        String storageObjectKey,
        String storageVersionId,
        Instant createdAt
) {
}
