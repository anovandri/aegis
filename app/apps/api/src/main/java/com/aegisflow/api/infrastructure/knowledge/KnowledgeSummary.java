package com.aegisflow.api.infrastructure.knowledge;

public record KnowledgeSummary(
        long totalSources,
        long freshSources,
        long staleSources,
        long needsReviewSources,
        long authoritativeSources,
        long supportingSources,
        long documentCount,
        long chunkCount,
        long citationUsageCount
) {
}
