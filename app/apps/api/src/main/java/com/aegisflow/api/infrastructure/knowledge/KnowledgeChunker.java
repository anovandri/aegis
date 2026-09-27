package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeChunker {
    private static final int TARGET_CHUNK_LENGTH = 900;
    private static final int MAX_CHUNK_LENGTH = 1_400;

    public List<String> chunk(String text) {
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.length() <= MAX_CHUNK_LENGTH) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String cleanParagraph = paragraph.trim();
            if (cleanParagraph.isBlank()) {
                continue;
            }
            if (current.length() + cleanParagraph.length() > TARGET_CHUNK_LENGTH && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (cleanParagraph.length() > MAX_CHUNK_LENGTH) {
                splitLongParagraph(cleanParagraph, chunks);
            } else {
                current.append(cleanParagraph).append("\n\n");
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private void splitLongParagraph(String paragraph, List<String> chunks) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + TARGET_CHUNK_LENGTH, paragraph.length());
            chunks.add(paragraph.substring(start, end).trim());
            start = end;
        }
    }
}
