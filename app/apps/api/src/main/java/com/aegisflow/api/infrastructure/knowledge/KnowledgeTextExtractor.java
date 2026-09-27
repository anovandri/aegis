package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class KnowledgeTextExtractor {
    public String extract(String fileName, String mediaType, byte[] content) {
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        String normalizedMediaType = mediaType.toLowerCase(Locale.ROOT);
        boolean supported = normalizedMediaType.startsWith("text/")
                || normalizedMediaType.equals("application/json")
                || normalizedName.endsWith(".md")
                || normalizedName.endsWith(".txt")
                || normalizedName.endsWith(".json");

        if (!supported) {
            throw new UnsupportedOperationException(
                    "Knowledge extraction currently supports text, markdown, and json only. Unsupported file: %s (%s)"
                            .formatted(fileName, mediaType)
            );
        }

        String text = new String(content, StandardCharsets.UTF_8).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Knowledge document is empty after text extraction");
        }
        return text;
    }
}
