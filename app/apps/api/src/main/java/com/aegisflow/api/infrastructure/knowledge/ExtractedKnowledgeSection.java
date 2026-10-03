package com.aegisflow.api.infrastructure.knowledge;

import java.util.Map;

public record ExtractedKnowledgeSection(
        String sectionType,
        String title,
        String text,
        Integer pageNumber,
        String sheetName,
        String cellRange,
        Integer startLine,
        Integer endLine,
        Map<String, String> metadata
) {
    public ExtractedKnowledgeSection {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
