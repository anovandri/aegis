package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class KnowledgeContentExtractorRegistry {
    private final List<KnowledgeContentExtractor> extractors;

    public KnowledgeContentExtractorRegistry(List<KnowledgeContentExtractor> extractors) {
        this.extractors = extractors.stream()
                .sorted(Comparator.comparing(KnowledgeContentExtractor::extractorName))
                .toList();
    }

    public ExtractedKnowledgeContent extract(String fileName, String mediaType, byte[] content) {
        return extractors.stream()
                .filter(extractor -> extractor.supports(fileName, mediaType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No knowledge content extractor supports file: %s (%s)".formatted(fileName, mediaType)
                ))
                .extract(fileName, mediaType, content);
    }
}
