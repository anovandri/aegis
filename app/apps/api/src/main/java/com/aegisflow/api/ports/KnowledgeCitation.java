package com.aegisflow.api.ports;

public record KnowledgeCitation(
        String sourceId,
        String sourceTitle,
        String sourceType,
        String authority,
        String excerpt,
        String relevanceReason
) {
}
