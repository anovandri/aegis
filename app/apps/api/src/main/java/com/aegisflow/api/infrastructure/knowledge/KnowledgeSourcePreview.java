package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;

public record KnowledgeSourcePreview(
        KnowledgeSource source,
        List<KnowledgeSourcePreviewMetric> metrics,
        KnowledgeSourceEligibility eligibility,
        List<KnowledgeSourceEvidence> evidence,
        List<KnowledgeSourceConnection> connections,
        List<KnowledgeSourceResource> resources,
        List<KnowledgeSyncRun> recentSyncRuns
) {
    public KnowledgeSourcePreview {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        connections = connections == null ? List.of() : List.copyOf(connections);
        resources = resources == null ? List.of() : List.copyOf(resources);
        recentSyncRuns = recentSyncRuns == null ? List.of() : List.copyOf(recentSyncRuns);
    }
}
