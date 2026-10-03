package com.aegisflow.api.infrastructure.knowledge;

public interface KnowledgeContentExtractor {
    String extractorName();

    boolean supports(String fileName, String mediaType);

    ExtractedKnowledgeContent extract(String fileName, String mediaType, byte[] content);
}
