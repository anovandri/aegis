package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

@Component
public class KnowledgeTextExtractor {
    private final KnowledgeContentExtractorRegistry extractorRegistry;

    public KnowledgeTextExtractor(KnowledgeContentExtractorRegistry extractorRegistry) {
        this.extractorRegistry = extractorRegistry;
    }

    public String extract(String fileName, String mediaType, byte[] content) {
        return extractorRegistry.extract(fileName, mediaType, content).text();
    }
}
