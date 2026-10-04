package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcKnowledgeDocumentRepository implements KnowledgeDocumentRepository {
    private final JdbcTemplate jdbcTemplate;
    private Boolean embeddingVectorColumnExists;

    public JdbcKnowledgeDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveSource(KnowledgeSource source) {
        if (findSource(source.sourceId()).isPresent()) {
            jdbcTemplate.update(
                    """
                            UPDATE knowledge_sources
                            SET name = ?,
                                source_type = ?,
                                authority = ?,
                                status = ?,
                                owner_team = ?,
                                enabled = ?,
                                freshness_sla_hours = ?,
                                sync_mode = ?,
                                sensitivity_policy = ?,
                                allowed_agents = ?,
                                workflow_states = ?,
                                tags = ?,
                                last_synced_at = ?,
                                review_due_at = ?
                            WHERE source_id = ?
                            """,
                    source.name(),
                    source.sourceType(),
                    source.authority(),
                    source.status(),
                    source.ownerTeam(),
                    source.enabled(),
                    source.freshnessSlaHours(),
                    source.syncMode(),
                    source.sensitivityPolicy(),
                    join(source.allowedAgents()),
                    join(source.workflowStates()),
                    join(source.tags()),
                    nullableTimestamp(source.lastSyncedAt()),
                    nullableTimestamp(source.reviewDueAt()),
                    source.sourceId()
            );
        } else {
            jdbcTemplate.update(
                    """
                            INSERT INTO knowledge_sources (
                                source_id,
                                name,
                                source_type,
                                authority,
                                status,
                                owner_team,
                                enabled,
                                freshness_sla_hours,
                                sync_mode,
                                sensitivity_policy,
                                allowed_agents,
                                workflow_states,
                                tags,
                                last_synced_at,
                                review_due_at,
                                created_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    source.sourceId(),
                    source.name(),
                    source.sourceType(),
                    source.authority(),
                    source.status(),
                    source.ownerTeam(),
                    source.enabled(),
                    source.freshnessSlaHours(),
                    source.syncMode(),
                    source.sensitivityPolicy(),
                    join(source.allowedAgents()),
                    join(source.workflowStates()),
                    join(source.tags()),
                    nullableTimestamp(source.lastSyncedAt()),
                    nullableTimestamp(source.reviewDueAt()),
                    Timestamp.from(source.createdAt())
            );
        }
    }

    @Override
    public Optional<KnowledgeSource> findSource(String sourceId) {
        List<KnowledgeSource> sources = jdbcTemplate.query(
                sourceSelectSql() + " AND ks.source_id = ? " + sourceGroupBySql(),
                sourceMapper(),
                sourceId
        );
        return sources.stream().findFirst();
    }

    @Override
    public List<KnowledgeSource> findSources(Optional<String> status) {
        if (status.isPresent()) {
            return jdbcTemplate.query(
                    sourceSelectSql() + " AND ks.status = ? " + sourceGroupBySql() + " ORDER BY LOWER(ks.name)",
                    sourceMapper(),
                    status.get()
            );
        }
        return jdbcTemplate.query(sourceSelectSql() + sourceGroupBySql() + " ORDER BY LOWER(ks.name)", sourceMapper());
    }

    @Override
    public void updateSourceSyncState(String sourceId, String status, Instant lastSyncedAt, Instant reviewDueAt) {
        jdbcTemplate.update(
                """
                        UPDATE knowledge_sources
                        SET status = ?,
                            last_synced_at = ?,
                            review_due_at = ?
                        WHERE source_id = ?
                        """,
                status,
                nullableTimestamp(lastSyncedAt),
                nullableTimestamp(reviewDueAt),
                sourceId
        );
    }

    @Override
    public KnowledgeSummary summarize() {
        List<KnowledgeSource> sources = findSources(Optional.empty());
        return new KnowledgeSummary(
                sources.size(),
                sources.stream().filter(source -> source.status().equals("FRESH")).count(),
                sources.stream().filter(source -> source.status().equals("STALE")).count(),
                sources.stream().filter(source -> source.status().equals("NEEDS_REVIEW") || source.status().equals("REVIEW_DUE")).count(),
                sources.stream().filter(source -> source.authority().equals("AUTHORITATIVE")).count(),
                sources.stream().filter(source -> source.authority().equals("SUPPORTING")).count(),
                count("knowledge_documents"),
                count("knowledge_chunks"),
                count("knowledge_citation_usages")
        );
    }

    @Override
    public boolean existsBySourceId(String sourceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_documents WHERE source_id = ?",
                Integer.class,
                sourceId
        );
        return count != null && count > 0;
    }

    @Override
    public void save(KnowledgeDocument document) {
        if (existsById(document.documentId())) {
            jdbcTemplate.update(
                    """
                            UPDATE knowledge_documents
                            SET source_id = ?,
                                source_title = ?,
                                source_type = ?,
                                authority = ?,
                                allowed_agents = ?,
                                workflow_states = ?,
                                tags = ?,
                                latest_version = ?
                            WHERE document_id = ?
                            """,
                    document.sourceId(),
                    document.sourceTitle(),
                    document.sourceType(),
                    document.authority(),
                    join(document.allowedAgents()),
                    join(document.workflowStates()),
                    join(document.tags()),
                    document.latestVersion(),
                    document.documentId()
            );
        } else {
            jdbcTemplate.update(
                    """
                            INSERT INTO knowledge_documents (
                                document_id,
                                source_id,
                                source_title,
                                source_type,
                                authority,
                                allowed_agents,
                                workflow_states,
                                tags,
                                latest_version,
                                created_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    document.documentId(),
                    document.sourceId(),
                    document.sourceTitle(),
                    document.sourceType(),
                    document.authority(),
                    join(document.allowedAgents()),
                    join(document.workflowStates()),
                    join(document.tags()),
                    document.latestVersion(),
                    Timestamp.from(document.createdAt())
            );
        }

        for (KnowledgeDocumentVersion version : document.versions()) {
            if (!existsVersion(document.documentId(), version.version())) {
                jdbcTemplate.update(
                        """
                                INSERT INTO knowledge_document_versions (
                                    document_id,
                                    version,
                                    file_name,
                                    media_type,
                                    size_bytes,
                                    content_hash,
                                    storage_bucket,
                                    storage_object_key,
                                    storage_version_id,
                                    extracted_text,
                                    created_at
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        document.documentId(),
                        version.version(),
                        version.fileName(),
                        version.mediaType(),
                        version.sizeBytes(),
                        version.contentHash(),
                        version.storageBucket(),
                        version.storageObjectKey(),
                        version.storageVersionId(),
                        version.extractedText(),
                        Timestamp.from(version.createdAt())
                );
            }
        }
    }

    @Override
    public List<KnowledgeDocument> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT
                            document_id,
                            source_id,
                            source_title,
                            source_type,
                            authority,
                            allowed_agents,
                            workflow_states,
                            tags,
                            latest_version,
                            created_at
                        FROM knowledge_documents
                        ORDER BY LOWER(source_title)
                        """,
                documentMapper()
        );
    }

    @Override
    public Optional<KnowledgeDocument> findById(UUID documentId) {
        List<KnowledgeDocument> documents = jdbcTemplate.query(
                """
                        SELECT
                            document_id,
                            source_id,
                            source_title,
                            source_type,
                            authority,
                            allowed_agents,
                            workflow_states,
                            tags,
                            latest_version,
                            created_at
                        FROM knowledge_documents
                        WHERE document_id = ?
                        """,
                documentMapper(),
                documentId
        );
        return documents.stream().findFirst();
    }

    @Override
    public Optional<KnowledgeDocument> findBySourceId(String sourceId) {
        List<KnowledgeDocument> documents = jdbcTemplate.query(
                """
                        SELECT
                            document_id,
                            source_id,
                            source_title,
                            source_type,
                            authority,
                            allowed_agents,
                            workflow_states,
                            tags,
                            latest_version,
                            created_at
                        FROM knowledge_documents
                        WHERE source_id = ?
                        """,
                documentMapper(),
                sourceId
        );
        return documents.stream().findFirst();
    }

    @Override
    public Optional<KnowledgeSyncedResourceDocument> findSyncedResourceDocument(String resourceKey) {
        List<KnowledgeSyncedResourceDocument> mappings = jdbcTemplate.query(
                """
                        SELECT
                            resource_key,
                            source_id,
                            connection_id,
                            external_id,
                            document_id,
                            last_content_hash,
                            updated_at
                        FROM knowledge_synced_resource_documents
                        WHERE resource_key = ?
                        """,
                syncedResourceDocumentMapper(),
                resourceKey
        );
        return mappings.stream().findFirst();
    }

    @Override
    public void saveSyncedResourceDocument(KnowledgeSyncedResourceDocument syncedResourceDocument) {
        if (findSyncedResourceDocument(syncedResourceDocument.resourceKey()).isPresent()) {
            jdbcTemplate.update(
                    """
                            UPDATE knowledge_synced_resource_documents
                            SET source_id = ?,
                                connection_id = ?,
                                external_id = ?,
                                document_id = ?,
                                last_content_hash = ?,
                                updated_at = ?
                            WHERE resource_key = ?
                            """,
                    syncedResourceDocument.sourceId(),
                    syncedResourceDocument.connectionId(),
                    syncedResourceDocument.externalId(),
                    syncedResourceDocument.documentId(),
                    syncedResourceDocument.lastContentHash(),
                    Timestamp.from(syncedResourceDocument.updatedAt()),
                    syncedResourceDocument.resourceKey()
            );
        } else {
            jdbcTemplate.update(
                    """
                            INSERT INTO knowledge_synced_resource_documents (
                                resource_key,
                                source_id,
                                connection_id,
                                external_id,
                                document_id,
                                last_content_hash,
                                updated_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    syncedResourceDocument.resourceKey(),
                    syncedResourceDocument.sourceId(),
                    syncedResourceDocument.connectionId(),
                    syncedResourceDocument.externalId(),
                    syncedResourceDocument.documentId(),
                    syncedResourceDocument.lastContentHash(),
                    Timestamp.from(syncedResourceDocument.updatedAt())
            );
        }
    }

    @Override
    public void saveChunks(List<KnowledgeChunk> chunks) {
        for (KnowledgeChunk chunk : chunks) {
            if (!existsChunk(chunk.chunkId())) {
                jdbcTemplate.update(
                        """
                                INSERT INTO knowledge_chunks (
                                    chunk_id,
                                    document_id,
                                    document_version,
                                    chunk_index,
                                    text,
                                    embedding_model,
                                    embedding,
                                    created_at
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                                """,
                        chunk.chunkId(),
                        chunk.documentId(),
                        chunk.documentVersion(),
                        chunk.chunkIndex(),
                        chunk.text(),
                        chunk.embeddingModel(),
                        encodeEmbedding(chunk.embedding())
                );
                savePgVectorEmbeddingIfAvailable(chunk);
            }
        }
    }

    @Override
    public boolean hasChunks(UUID documentId, int documentVersion) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunks WHERE document_id = ? AND document_version = ?",
                Integer.class,
                documentId,
                documentVersion
        );
        return count != null && count > 0;
    }

    @Override
    public List<KnowledgeChunk> findLatestChunksForAgentAndState(String agentName, String workflowState) {
        return jdbcTemplate.query(
                """
                        SELECT
                            kc.chunk_id,
                            kd.document_id,
                            kd.source_id,
                            kd.source_title,
                            kd.source_type,
                            kd.authority,
                            kd.allowed_agents,
                            kd.workflow_states,
                            kd.tags,
                            kc.document_version,
                            kc.chunk_index,
                            kc.text,
                            kc.embedding_model,
                            kc.embedding
                        FROM knowledge_chunks kc
                        JOIN knowledge_documents kd
                            ON kd.document_id = kc.document_id
                        WHERE kc.document_version = kd.latest_version
                          AND kd.allowed_agents LIKE ?
                          AND kd.workflow_states LIKE ?
                        ORDER BY LOWER(kd.source_title), kc.chunk_index
                        """,
                chunkMapper(),
                "%" + agentName + "%",
                "%" + workflowState + "%"
        );
    }

    @Override
    public List<KnowledgeChunk> findLatestChunks() {
        return jdbcTemplate.query(
                latestChunkSelectSql() + " ORDER BY LOWER(kd.source_title), kc.chunk_index",
                chunkMapper()
        );
    }

    @Override
    public void saveSyncRun(KnowledgeSyncRun syncRun) {
        jdbcTemplate.update(
                """
                        INSERT INTO knowledge_sync_runs (
                            sync_run_id,
                            source_id,
                            status,
                            started_at,
                            completed_at,
                            records_changed,
                            error_message
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                syncRun.syncRunId(),
                syncRun.sourceId(),
                syncRun.status(),
                Timestamp.from(syncRun.startedAt()),
                nullableTimestamp(syncRun.completedAt()),
                syncRun.recordsChanged(),
                syncRun.errorMessage()
        );
    }

    @Override
    public List<KnowledgeSyncRun> findSyncRuns(String sourceId) {
        return jdbcTemplate.query(
                """
                        SELECT sync_run_id, source_id, status, started_at, completed_at, records_changed, error_message
                        FROM knowledge_sync_runs
                        WHERE source_id = ?
                        ORDER BY started_at DESC
                        """,
                syncRunMapper(),
                sourceId
        );
    }

    @Override
    public void saveAuditEvent(KnowledgeAuditEvent event) {
        jdbcTemplate.update(
                """
                        INSERT INTO knowledge_audit_events (
                            event_id,
                            source_id,
                            action,
                            actor,
                            details,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                event.eventId(),
                event.sourceId(),
                event.action(),
                event.actor(),
                event.details(),
                Timestamp.from(event.createdAt())
        );
    }

    @Override
    public List<KnowledgeAuditEvent> findAuditEvents(Optional<String> sourceId) {
        if (sourceId.isPresent()) {
            return jdbcTemplate.query(
                    """
                            SELECT event_id, source_id, action, actor, details, created_at
                            FROM knowledge_audit_events
                            WHERE source_id = ?
                            ORDER BY created_at DESC
                            """,
                    auditMapper(),
                    sourceId.get()
            );
        }
        return jdbcTemplate.query(
                """
                        SELECT event_id, source_id, action, actor, details, created_at
                        FROM knowledge_audit_events
                        ORDER BY created_at DESC
                        """,
                auditMapper()
        );
    }

    @Override
    public void saveConnection(KnowledgeSourceConnection connection) {
        if (findConnection(connection.connectionId()).isPresent()) {
            jdbcTemplate.update(
                    """
                            UPDATE knowledge_source_connections
                            SET source_id = ?,
                                adapter_type = ?,
                                connection_name = ?,
                                connection_status = ?,
                                resource_locator = ?,
                                auth_type = ?,
                                credential_ref = ?,
                                config_json = ?,
                                last_checked_at = ?,
                                last_error = ?
                            WHERE connection_id = ?
                            """,
                    connection.sourceId(),
                    connection.adapterType(),
                    connection.connectionName(),
                    connection.connectionStatus(),
                    connection.resourceLocator(),
                    connection.authType(),
                    connection.credentialRef(),
                    connection.configJson(),
                    nullableTimestamp(connection.lastCheckedAt()),
                    connection.lastError(),
                    connection.connectionId()
            );
        } else {
            jdbcTemplate.update(
                    """
                            INSERT INTO knowledge_source_connections (
                                connection_id,
                                source_id,
                                adapter_type,
                                connection_name,
                                connection_status,
                                resource_locator,
                                auth_type,
                                credential_ref,
                                config_json,
                                last_checked_at,
                                last_error,
                                created_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    connection.connectionId(),
                    connection.sourceId(),
                    connection.adapterType(),
                    connection.connectionName(),
                    connection.connectionStatus(),
                    connection.resourceLocator(),
                    connection.authType(),
                    connection.credentialRef(),
                    connection.configJson(),
                    nullableTimestamp(connection.lastCheckedAt()),
                    connection.lastError(),
                    Timestamp.from(connection.createdAt())
            );
        }
    }

    @Override
    public Optional<KnowledgeSourceConnection> findConnection(UUID connectionId) {
        List<KnowledgeSourceConnection> connections = jdbcTemplate.query(
                """
                        SELECT
                            connection_id,
                            source_id,
                            adapter_type,
                            connection_name,
                            connection_status,
                            resource_locator,
                            auth_type,
                            credential_ref,
                            config_json,
                            last_checked_at,
                            last_error,
                            created_at
                        FROM knowledge_source_connections
                        WHERE connection_id = ?
                        """,
                connectionMapper(),
                connectionId
        );
        return connections.stream().findFirst();
    }

    @Override
    public List<KnowledgeSourceConnection> findConnections(String sourceId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            connection_id,
                            source_id,
                            adapter_type,
                            connection_name,
                            connection_status,
                            resource_locator,
                            auth_type,
                            credential_ref,
                            config_json,
                            last_checked_at,
                            last_error,
                            created_at
                        FROM knowledge_source_connections
                        WHERE source_id = ?
                        ORDER BY created_at DESC
                        """,
                connectionMapper(),
                sourceId
        );
    }

    @Override
    public void updateConnectionStatus(UUID connectionId, String status, Instant checkedAt, String lastError) {
        jdbcTemplate.update(
                """
                        UPDATE knowledge_source_connections
                        SET connection_status = ?,
                            last_checked_at = ?,
                            last_error = ?
                        WHERE connection_id = ?
                        """,
                status,
                nullableTimestamp(checkedAt),
                lastError,
                connectionId
        );
    }

    @Override
    public void saveResources(List<KnowledgeSourceResource> resources) {
        for (KnowledgeSourceResource resource : resources) {
            if (existsResource(resource.connectionId(), resource.externalId())) {
                jdbcTemplate.update(
                        """
                                UPDATE knowledge_source_resources
                                SET resource_type = ?,
                                    title = ?,
                                    uri = ?,
                                    version_ref = ?,
                                    content_hash = ?,
                                    status = ?,
                                    last_seen_at = ?
                                WHERE connection_id = ?
                                  AND external_id = ?
                                """,
                        resource.resourceType(),
                        resource.title(),
                        resource.uri(),
                        resource.versionRef(),
                        resource.contentHash(),
                        resource.status(),
                        Timestamp.from(resource.lastSeenAt()),
                        resource.connectionId(),
                        resource.externalId()
                );
            } else {
                jdbcTemplate.update(
                        """
                                INSERT INTO knowledge_source_resources (
                                    resource_id,
                                    connection_id,
                                    external_id,
                                    resource_type,
                                    title,
                                    uri,
                                    version_ref,
                                    content_hash,
                                    status,
                                    last_seen_at
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        resource.resourceId(),
                        resource.connectionId(),
                        resource.externalId(),
                        resource.resourceType(),
                        resource.title(),
                        resource.uri(),
                        resource.versionRef(),
                        resource.contentHash(),
                        resource.status(),
                        Timestamp.from(resource.lastSeenAt())
                );
            }
        }
    }

    @Override
    public Optional<KnowledgeSourceResource> findResource(UUID resourceId) {
        List<KnowledgeSourceResource> resources = jdbcTemplate.query(
                """
                        SELECT
                            resource_id,
                            connection_id,
                            external_id,
                            resource_type,
                            title,
                            uri,
                            version_ref,
                            content_hash,
                            status,
                            last_seen_at
                        FROM knowledge_source_resources
                        WHERE resource_id = ?
                        """,
                resourceMapper(),
                resourceId
        );
        return resources.stream().findFirst();
    }

    @Override
    public List<KnowledgeSourceResource> findResources(UUID connectionId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            resource_id,
                            connection_id,
                            external_id,
                            resource_type,
                            title,
                            uri,
                            version_ref,
                            content_hash,
                            status,
                            last_seen_at
                        FROM knowledge_source_resources
                        WHERE connection_id = ?
                        ORDER BY LOWER(title)
                        """,
                resourceMapper(),
                connectionId
        );
    }

    private RowMapper<KnowledgeDocument> documentMapper() {
        return (resultSet, rowNumber) -> {
            UUID documentId = uuid(resultSet, "document_id");
            return new KnowledgeDocument(
                    documentId,
                    resultSet.getString("source_id"),
                    resultSet.getString("source_title"),
                    resultSet.getString("source_type"),
                    resultSet.getString("authority"),
                    split(resultSet.getString("allowed_agents")),
                    split(resultSet.getString("workflow_states")),
                    split(resultSet.getString("tags")),
                    resultSet.getInt("latest_version"),
                    instant(resultSet, "created_at"),
                    findVersions(documentId)
            );
        };
    }

    private RowMapper<KnowledgeSyncedResourceDocument> syncedResourceDocumentMapper() {
        return (resultSet, rowNumber) -> new KnowledgeSyncedResourceDocument(
                resultSet.getString("resource_key"),
                resultSet.getString("source_id"),
                uuid(resultSet, "connection_id"),
                resultSet.getString("external_id"),
                uuid(resultSet, "document_id"),
                resultSet.getString("last_content_hash"),
                instant(resultSet, "updated_at")
        );
    }

    private String sourceSelectSql() {
        return """
                SELECT
                    ks.source_id,
                    ks.name,
                    ks.source_type,
                    ks.authority,
                    ks.status,
                    ks.owner_team,
                    ks.enabled,
                    ks.freshness_sla_hours,
                    ks.sync_mode,
                    ks.sensitivity_policy,
                    ks.allowed_agents,
                    ks.workflow_states,
                    ks.tags,
                    ks.last_synced_at,
                    ks.review_due_at,
                    ks.created_at,
                    COUNT(DISTINCT kd.document_id) AS document_count,
                    COUNT(DISTINCT kc.chunk_id) AS chunk_count,
                    COUNT(DISTINCT kcu.usage_id) AS agent_use_count
                FROM knowledge_sources ks
                LEFT JOIN knowledge_synced_resource_documents ksrd
                    ON ksrd.source_id = ks.source_id
                LEFT JOIN knowledge_documents kd
                    ON kd.source_id = ks.source_id
                    OR kd.document_id = ksrd.document_id
                LEFT JOIN knowledge_chunks kc
                    ON kc.document_id = kd.document_id
                LEFT JOIN knowledge_citation_usages kcu
                    ON kcu.source_id = ks.source_id
                WHERE 1 = 1
                """;
    }

    private String sourceGroupBySql() {
        return """
                GROUP BY
                    ks.source_id,
                    ks.name,
                    ks.source_type,
                    ks.authority,
                    ks.status,
                    ks.owner_team,
                    ks.enabled,
                    ks.freshness_sla_hours,
                    ks.sync_mode,
                    ks.sensitivity_policy,
                    ks.allowed_agents,
                    ks.workflow_states,
                    ks.tags,
                    ks.last_synced_at,
                    ks.review_due_at,
                    ks.created_at
                """;
    }

    private RowMapper<KnowledgeSource> sourceMapper() {
        return (resultSet, rowNumber) -> new KnowledgeSource(
                resultSet.getString("source_id"),
                resultSet.getString("name"),
                resultSet.getString("source_type"),
                resultSet.getString("authority"),
                resultSet.getString("status"),
                resultSet.getString("owner_team"),
                resultSet.getBoolean("enabled"),
                resultSet.getInt("freshness_sla_hours"),
                resultSet.getString("sync_mode"),
                resultSet.getString("sensitivity_policy"),
                split(resultSet.getString("allowed_agents")),
                split(resultSet.getString("workflow_states")),
                split(resultSet.getString("tags")),
                nullableInstant(resultSet, "last_synced_at"),
                nullableInstant(resultSet, "review_due_at"),
                instant(resultSet, "created_at"),
                resultSet.getLong("document_count"),
                resultSet.getLong("chunk_count"),
                resultSet.getLong("agent_use_count")
        );
    }

    private List<KnowledgeDocumentVersion> findVersions(UUID documentId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            version,
                            file_name,
                            media_type,
                            size_bytes,
                            content_hash,
                            storage_bucket,
                            storage_object_key,
                            storage_version_id,
                            extracted_text,
                            created_at
                        FROM knowledge_document_versions
                        WHERE document_id = ?
                        ORDER BY version
                        """,
                versionMapper(),
                documentId
        );
    }

    private RowMapper<KnowledgeDocumentVersion> versionMapper() {
        return (resultSet, rowNumber) -> new KnowledgeDocumentVersion(
                resultSet.getInt("version"),
                resultSet.getString("file_name"),
                resultSet.getString("media_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("content_hash"),
                resultSet.getString("storage_bucket"),
                resultSet.getString("storage_object_key"),
                resultSet.getString("storage_version_id"),
                resultSet.getString("extracted_text"),
                instant(resultSet, "created_at")
        );
    }

    private RowMapper<KnowledgeChunk> chunkMapper() {
        return (resultSet, rowNumber) -> new KnowledgeChunk(
                uuid(resultSet, "chunk_id"),
                uuid(resultSet, "document_id"),
                resultSet.getString("source_id"),
                resultSet.getString("source_title"),
                resultSet.getString("source_type"),
                resultSet.getString("authority"),
                split(resultSet.getString("allowed_agents")),
                split(resultSet.getString("workflow_states")),
                split(resultSet.getString("tags")),
                resultSet.getInt("document_version"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("text"),
                resultSet.getString("embedding_model"),
                decodeEmbedding(resultSet.getString("embedding"))
        );
    }

    private RowMapper<KnowledgeSyncRun> syncRunMapper() {
        return (resultSet, rowNumber) -> new KnowledgeSyncRun(
                uuid(resultSet, "sync_run_id"),
                resultSet.getString("source_id"),
                resultSet.getString("status"),
                instant(resultSet, "started_at"),
                nullableInstant(resultSet, "completed_at"),
                resultSet.getInt("records_changed"),
                resultSet.getString("error_message")
        );
    }

    private RowMapper<KnowledgeAuditEvent> auditMapper() {
        return (resultSet, rowNumber) -> new KnowledgeAuditEvent(
                uuid(resultSet, "event_id"),
                resultSet.getString("source_id"),
                resultSet.getString("action"),
                resultSet.getString("actor"),
                resultSet.getString("details"),
                instant(resultSet, "created_at")
        );
    }

    private RowMapper<KnowledgeSourceConnection> connectionMapper() {
        return (resultSet, rowNumber) -> new KnowledgeSourceConnection(
                uuid(resultSet, "connection_id"),
                resultSet.getString("source_id"),
                resultSet.getString("adapter_type"),
                resultSet.getString("connection_name"),
                resultSet.getString("connection_status"),
                resultSet.getString("resource_locator"),
                resultSet.getString("auth_type"),
                resultSet.getString("credential_ref"),
                resultSet.getString("config_json"),
                nullableInstant(resultSet, "last_checked_at"),
                resultSet.getString("last_error"),
                instant(resultSet, "created_at")
        );
    }

    private RowMapper<KnowledgeSourceResource> resourceMapper() {
        return (resultSet, rowNumber) -> new KnowledgeSourceResource(
                uuid(resultSet, "resource_id"),
                uuid(resultSet, "connection_id"),
                resultSet.getString("external_id"),
                resultSet.getString("resource_type"),
                resultSet.getString("title"),
                resultSet.getString("uri"),
                resultSet.getString("version_ref"),
                resultSet.getString("content_hash"),
                resultSet.getString("status"),
                instant(resultSet, "last_seen_at")
        );
    }

    private String latestChunkSelectSql() {
        return """
                SELECT
                    kc.chunk_id,
                    kd.document_id,
                    kd.source_id,
                    kd.source_title,
                    kd.source_type,
                    kd.authority,
                    kd.allowed_agents,
                    kd.workflow_states,
                    kd.tags,
                    kc.document_version,
                    kc.chunk_index,
                    kc.text,
                    kc.embedding_model,
                    kc.embedding
                FROM knowledge_chunks kc
                JOIN knowledge_documents kd
                    ON kd.document_id = kc.document_id
                WHERE kc.document_version = kd.latest_version
                """;
    }

    private boolean existsById(UUID documentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_documents WHERE document_id = ?",
                Integer.class,
                documentId
        );
        return count != null && count > 0;
    }

    private boolean existsVersion(UUID documentId, int version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_document_versions WHERE document_id = ? AND version = ?",
                Integer.class,
                documentId,
                version
        );
        return count != null && count > 0;
    }

    private boolean existsChunk(UUID chunkId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunks WHERE chunk_id = ?",
                Integer.class,
                chunkId
        );
        return count != null && count > 0;
    }

    private boolean existsResource(UUID connectionId, String externalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_source_resources WHERE connection_id = ? AND external_id = ?",
                Integer.class,
                connectionId,
                externalId
        );
        return count != null && count > 0;
    }

    private String join(List<String> values) {
        return String.join(",", values);
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String encodeEmbedding(List<Double> embedding) {
        return embedding.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private void savePgVectorEmbeddingIfAvailable(KnowledgeChunk chunk) {
        if (!hasEmbeddingVectorColumn()) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE knowledge_chunks SET embedding_vector = ?::vector WHERE chunk_id = ?",
                toPgVectorLiteral(chunk.embedding()),
                chunk.chunkId()
        );
    }

    private boolean hasEmbeddingVectorColumn() {
        if (embeddingVectorColumnExists != null) {
            return embeddingVectorColumnExists;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_name = 'knowledge_chunks'
                          AND column_name = 'embedding_vector'
                        """,
                Integer.class
        );
        embeddingVectorColumnExists = count != null && count > 0;
        return embeddingVectorColumnExists;
    }

    private String toPgVectorLiteral(List<Double> embedding) {
        return "[" + encodeEmbedding(embedding) + "]";
    }

    private List<Double> decodeEmbedding(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Double> embedding = new ArrayList<>();
        for (String item : value.split(",")) {
            embedding.add(Double.parseDouble(item));
        }
        return embedding;
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp nullableTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
