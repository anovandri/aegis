package com.aegisflow.api.application;

import com.aegisflow.api.infrastructure.knowledge.CodeKnowledgeParserRegistry;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeChunk;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeChunker;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeDocumentRepository;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeEmbeddingPort;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeIndexingPipeline;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSource;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceAdapterRegistry;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceConnection;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourcePreview;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceResource;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSyncRun;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeTextExtractor;
import com.aegisflow.api.ports.DocumentStoragePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {
    private final KnowledgeDocumentRepository repository = mock(KnowledgeDocumentRepository.class);
    private final KnowledgeService service = new KnowledgeService(
            Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC),
            mock(DocumentStoragePort.class),
            mock(KnowledgeTextExtractor.class),
            repository,
            mock(KnowledgeEmbeddingPort.class),
            new KnowledgeIndexingPipeline(
                    repository,
                    mock(KnowledgeChunker.class),
                    mock(KnowledgeEmbeddingPort.class),
                    mock(CodeKnowledgeParserRegistry.class)
            ),
            mock(KnowledgeSourceAdapterRegistry.class),
            new ObjectMapper()
    );

    @Test
    void previewSourceReturnsMetricsEligibilityEvidenceConnectionsResourcesAndSyncRuns() {
        KnowledgeSource source = source("service-catalog", "FRESH", true, 2, 4, 7);
        UUID connectionId = UUID.randomUUID();
        KnowledgeSourceConnection connection = connection(connectionId, "service-catalog", "CONNECTED");
        KnowledgeSourceResource resource = resource(connectionId, "payment-service");
        KnowledgeSyncRun syncRun = syncRun("service-catalog", 1);
        KnowledgeChunk chunk = chunk("service-catalog", "Service Catalog", "Payment service owns QRIS reconciliation and timeout metadata.");
        when(repository.findSource("service-catalog")).thenReturn(Optional.of(source));
        when(repository.findConnections("service-catalog")).thenReturn(List.of(connection));
        when(repository.findResources(connectionId)).thenReturn(List.of(resource));
        when(repository.findSyncRuns("service-catalog")).thenReturn(List.of(syncRun));
        when(repository.findLatestChunks()).thenReturn(List.of(chunk));

        KnowledgeSourcePreview preview = service.previewSource("service-catalog").orElseThrow();

        assertThat(preview.source().sourceId()).isEqualTo("service-catalog");
        assertThat(preview.metrics()).extracting("label")
                .containsExactly("Documents", "Chunks", "Resources", "Connections", "Owners", "Agent uses");
        assertThat(preview.eligibility().eligible()).isTrue();
        assertThat(preview.eligibility().status()).isEqualTo("ELIGIBLE");
        assertThat(preview.evidence()).hasSize(1);
        assertThat(preview.evidence().getFirst().detail()).contains("QRIS reconciliation");
        assertThat(preview.connections()).containsExactly(connection);
        assertThat(preview.resources()).containsExactly(resource);
        assertThat(preview.recentSyncRuns()).containsExactly(syncRun);
    }

    @Test
    void previewSourceBlocksEligibilityWhenSourceIsNotReadyForAgentReuse() {
        KnowledgeSource source = source("standards", "NEEDS_REVIEW", false, 0, 0, 0);
        when(repository.findSource("standards")).thenReturn(Optional.of(source));
        when(repository.findConnections("standards")).thenReturn(List.of());
        when(repository.findSyncRuns("standards")).thenReturn(List.of());
        when(repository.findLatestChunks()).thenReturn(List.of());

        KnowledgeSourcePreview preview = service.previewSource("standards").orElseThrow();

        assertThat(preview.eligibility().eligible()).isFalse();
        assertThat(preview.eligibility().status()).isEqualTo("BLOCKED");
        assertThat(preview.eligibility().reasons())
                .contains("Source is disabled.", "Source status is NEEDS_REVIEW.", "No indexed chunks are available for retrieval.");
    }

    @Test
    void previewSourceIncludesSyncedResourceChunksByStableSourcePrefix() {
        KnowledgeSource source = source("gitlab-source", "FRESH", true, 1, 1, 0);
        KnowledgeChunk directChunk = chunk("other-source", "Other Source", "Unrelated service metadata.");
        KnowledgeChunk syncedChunk = chunk("gitlab-source:abcdef1234567890", "Repository README", "GitLab README explains service ownership.");
        when(repository.findSource("gitlab-source")).thenReturn(Optional.of(source));
        when(repository.findConnections("gitlab-source")).thenReturn(List.of());
        when(repository.findSyncRuns("gitlab-source")).thenReturn(List.of());
        when(repository.findLatestChunks()).thenReturn(List.of(directChunk, syncedChunk));

        KnowledgeSourcePreview preview = service.previewSource("gitlab-source").orElseThrow();

        assertThat(preview.evidence()).hasSize(1);
        assertThat(preview.evidence().getFirst().sourceTitle()).isEqualTo("Repository README");
    }

    private KnowledgeSource source(String sourceId, String status, boolean enabled, long documentCount, long chunkCount, long agentUseCount) {
        return new KnowledgeSource(
                sourceId,
                "Source " + sourceId,
                "SERVICE_CATALOG",
                "AUTHORITATIVE",
                status,
                "Architecture",
                enabled,
                24,
                "MANUAL_PULL",
                "INTERNAL",
                List.of("Requirement Analyst Agent", "Architecture Agent"),
                List.of("REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS"),
                List.of("payment", "qris"),
                Instant.parse("2026-10-08T00:00:00Z"),
                Instant.parse("2026-10-09T00:00:00Z"),
                Instant.parse("2026-10-07T00:00:00Z"),
                documentCount,
                chunkCount,
                agentUseCount
        );
    }

    private KnowledgeSourceConnection connection(UUID connectionId, String sourceId, String status) {
        return new KnowledgeSourceConnection(
                connectionId,
                sourceId,
                "GITLAB",
                "Source connection",
                status,
                "resource",
                "PAT",
                "env://TOKEN",
                "{}",
                Instant.parse("2026-10-08T00:00:00Z"),
                null,
                Instant.parse("2026-10-07T00:00:00Z")
        );
    }

    private KnowledgeSourceResource resource(UUID connectionId, String externalId) {
        return new KnowledgeSourceResource(
                UUID.randomUUID(),
                connectionId,
                externalId,
                "REPOSITORY_FILE",
                "Payment Service",
                "https://example.test/payment-service",
                "main",
                "hash",
                "DISCOVERED",
                Instant.parse("2026-10-08T00:00:00Z")
        );
    }

    private KnowledgeSyncRun syncRun(String sourceId, int recordsChanged) {
        return new KnowledgeSyncRun(
                UUID.randomUUID(),
                sourceId,
                "SUCCEEDED",
                Instant.parse("2026-10-08T00:00:00Z"),
                Instant.parse("2026-10-08T00:00:01Z"),
                recordsChanged,
                null
        );
    }

    private KnowledgeChunk chunk(String sourceId, String sourceTitle, String text) {
        return new KnowledgeChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                sourceId,
                sourceTitle,
                "SERVICE_CATALOG",
                "AUTHORITATIVE",
                List.of("Requirement Analyst Agent", "Architecture Agent"),
                List.of("REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS"),
                List.of("payment"),
                1,
                0,
                text,
                "local-hash",
                List.of(0.1, 0.2)
        );
    }
}
