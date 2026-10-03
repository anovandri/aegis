package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;
import java.util.Map;

public record ExtractedKnowledgeContent(
        String extractionType,
        String text,
        List<ExtractedKnowledgeSection> sections,
        Map<String, String> metadata,
        List<String> warnings
) {
    public ExtractedKnowledgeContent {
        sections = sections == null ? List.of() : List.copyOf(sections);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
