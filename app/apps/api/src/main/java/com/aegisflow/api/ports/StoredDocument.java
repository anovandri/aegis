package com.aegisflow.api.ports;

public record StoredDocument(
        String bucket,
        String objectKey,
        String storageVersionId
) {
}
