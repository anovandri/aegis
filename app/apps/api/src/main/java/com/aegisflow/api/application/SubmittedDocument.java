package com.aegisflow.api.application;

public record SubmittedDocument(
        String fileName,
        String mediaType,
        byte[] content
) {
    public long sizeBytes() {
        return content.length;
    }
}
