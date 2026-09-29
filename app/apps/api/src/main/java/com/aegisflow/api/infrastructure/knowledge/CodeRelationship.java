package com.aegisflow.api.infrastructure.knowledge;

public record CodeRelationship(
        String sourceFile,
        String sourceSymbol,
        String relationshipType,
        String targetFile,
        String targetSymbol
) {
}
