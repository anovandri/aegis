package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PlainTextKnowledgeContentExtractor implements KnowledgeContentExtractor {
    @Override
    public String extractorName() {
        return "plain-text";
    }

    @Override
    public boolean supports(String fileName, String mediaType) {
        String normalizedName = normalize(fileName);
        String normalizedMediaType = normalize(mediaType);
        return normalizedMediaType.startsWith("text/")
                || normalizedMediaType.equals("application/json")
                || normalizedName.endsWith(".md")
                || normalizedName.endsWith(".txt")
                || normalizedName.endsWith(".json")
                || normalizedName.endsWith(".go")
                || normalizedName.endsWith(".yaml")
                || normalizedName.endsWith(".yml")
                || normalizedName.endsWith(".sql")
                || normalizedName.endsWith(".csv");
    }

    @Override
    public ExtractedKnowledgeContent extract(String fileName, String mediaType, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Knowledge document is empty after text extraction");
        }
        return new ExtractedKnowledgeContent(
                extractionType(fileName, mediaType),
                text,
                List.of(new ExtractedKnowledgeSection(
                        extractionType(fileName, mediaType),
                        fileName,
                        text,
                        null,
                        null,
                        null,
                        1,
                        lineCount(text),
                        Map.of("extractor", extractorName())
                )),
                Map.of("extractor", extractorName()),
                List.of()
        );
    }

    private String extractionType(String fileName, String mediaType) {
        String normalizedName = normalize(fileName);
        if (normalizedName.endsWith(".go")) {
            return "SOURCE_CODE";
        }
        if (normalizedName.endsWith(".md")) {
            return "MARKDOWN";
        }
        if (normalizedName.endsWith(".csv")) {
            return "CSV";
        }
        if (normalizedName.endsWith(".json")) {
            return "JSON";
        }
        return "TEXT";
    }

    private int lineCount(String text) {
        return text.isEmpty() ? 0 : text.split("\\R", -1).length;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
