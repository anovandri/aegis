package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocumentRepository {
    void saveSource(KnowledgeSource source);

    Optional<KnowledgeSource> findSource(String sourceId);

    List<KnowledgeSource> findSources(Optional<String> status);

    void updateSourceSyncState(String sourceId, String status, java.time.Instant lastSyncedAt, java.time.Instant reviewDueAt);

    KnowledgeSummary summarize();

    boolean existsBySourceId(String sourceId);

    void save(KnowledgeDocument document);

    List<KnowledgeDocument> findAll();

    Optional<KnowledgeDocument> findById(UUID documentId);

    Optional<KnowledgeDocument> findBySourceId(String sourceId);

    Optional<KnowledgeSyncedResourceDocument> findSyncedResourceDocument(String resourceKey);

    void saveSyncedResourceDocument(KnowledgeSyncedResourceDocument syncedResourceDocument);

    void saveChunks(List<KnowledgeChunk> chunks);

    boolean hasChunks(UUID documentId, int documentVersion);

    List<KnowledgeChunk> findLatestChunks();

    List<KnowledgeChunk> findLatestChunksForAgentAndState(String agentName, String workflowState);

    void saveSyncRun(KnowledgeSyncRun syncRun);

    List<KnowledgeSyncRun> findSyncRuns(String sourceId);

    void saveAuditEvent(KnowledgeAuditEvent event);

    List<KnowledgeAuditEvent> findAuditEvents(Optional<String> sourceId);

    void saveConnection(KnowledgeSourceConnection connection);

    Optional<KnowledgeSourceConnection> findConnection(UUID connectionId);

    List<KnowledgeSourceConnection> findConnections(String sourceId);

    void updateConnectionStatus(UUID connectionId, String status, java.time.Instant checkedAt, String lastError);

    void saveResources(List<KnowledgeSourceResource> resources);

    Optional<KnowledgeSourceResource> findResource(UUID resourceId);

    List<KnowledgeSourceResource> findResources(UUID connectionId);
}
