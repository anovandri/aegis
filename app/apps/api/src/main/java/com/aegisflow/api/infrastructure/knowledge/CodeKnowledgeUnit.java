package com.aegisflow.api.infrastructure.knowledge;

import java.util.Map;

public record CodeKnowledgeUnit(
        String language,
        String fileName,
        String symbolType,
        String symbolName,
        int startLine,
        int endLine,
        int startByte,
        int endByte,
        String text,
        Map<String, String> metadata
) {
    public CodeKnowledgeUnit {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String toKnowledgeText() {
        return """
                Language: %s
                File: %s
                Symbol Type: %s
                Symbol Name: %s
                Lines: %d-%d

                %s
                """.formatted(
                language,
                fileName,
                symbolType,
                symbolName,
                startLine,
                endLine,
                text
        ).trim();
    }
}
