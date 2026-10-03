package com.aegisflow.api.application;

import com.aegisflow.api.infrastructure.knowledge.KnowledgeDocument;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeDocumentRepository;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeDocumentVersion;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeAdapterDescriptor;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeAuditEvent;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeEmbeddingPort;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeIndexingPipeline;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSeedDocument;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceAdapter;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceAdapterRegistry;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeConnectionCheckResult;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSource;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceConnection;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourcePayload;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceResource;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSyncedResourceDocument;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSummary;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSyncRun;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeTextExtractor;
import com.aegisflow.api.ports.DocumentStoragePort;
import com.aegisflow.api.ports.KnowledgeCitation;
import com.aegisflow.api.ports.StoredDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class KnowledgeService {
    private final Clock clock;
    private final DocumentStoragePort documentStoragePort;
    private final KnowledgeTextExtractor textExtractor;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeEmbeddingPort knowledgeEmbeddingPort;
    private final KnowledgeIndexingPipeline knowledgeIndexingPipeline;
    private final KnowledgeSourceAdapterRegistry knowledgeSourceAdapterRegistry;
    private final ObjectMapper objectMapper;

    public KnowledgeService(
            Clock clock,
            DocumentStoragePort documentStoragePort,
            KnowledgeTextExtractor textExtractor,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeEmbeddingPort knowledgeEmbeddingPort,
            KnowledgeIndexingPipeline knowledgeIndexingPipeline,
            KnowledgeSourceAdapterRegistry knowledgeSourceAdapterRegistry,
            ObjectMapper objectMapper
    ) {
        this.clock = clock;
        this.documentStoragePort = documentStoragePort;
        this.textExtractor = textExtractor;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeEmbeddingPort = knowledgeEmbeddingPort;
        this.knowledgeIndexingPipeline = knowledgeIndexingPipeline;
        this.knowledgeSourceAdapterRegistry = knowledgeSourceAdapterRegistry;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void seedInitialKnowledge() {
        try {
            List<KnowledgeSeedDocument> seeds = objectMapper.readValue(
                    new ClassPathResource("knowledge/requirement-analysis-seed.json").getInputStream(),
                    new TypeReference<>() {
                    }
            );
            for (KnowledgeSeedDocument seed : seeds) {
                if (!knowledgeDocumentRepository.existsBySourceId(seed.sourceId())) {
                    createDocument(
                            seed.sourceTitle(),
                            seed.sourceId(),
                            seed.sourceType(),
                            seed.authority(),
                            seed.allowedAgents(),
                            seed.workflowStates(),
                            seed.tags(),
                            new SubmittedDocument(
                                    seed.sourceId() + ".md",
                                    "text/markdown",
                                    seed.excerpt().getBytes(StandardCharsets.UTF_8)
                            )
                    );
                }
            }
            backfillMissingChunks();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to seed local knowledge documents", exception);
        }
    }

    @Transactional
    public KnowledgeDocument createDocument(
            String sourceTitle,
            String sourceId,
            String sourceType,
            String authority,
            List<String> allowedAgents,
            List<String> workflowStates,
            List<String> tags,
            SubmittedDocument file
    ) {
        UUID documentId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        String canonicalSourceId = sourceId == null || sourceId.isBlank() ? documentId.toString() : sourceId;
        ensureSourceForDocument(
                canonicalSourceId,
                sourceTitle,
                sourceType,
                authority,
                allowedAgents,
                workflowStates,
                tags,
                now
        );
        KnowledgeDocumentVersion version = createVersion(documentId, 1, file, now);
        KnowledgeDocument document = new KnowledgeDocument(
                documentId,
                canonicalSourceId,
                sourceTitle,
                sourceType,
                authority,
                List.copyOf(allowedAgents),
                List.copyOf(workflowStates),
                List.copyOf(tags),
                1,
                now,
                new ArrayList<>(List.of(version))
        );
        knowledgeDocumentRepository.save(document);
        indexVersion(document, version);
        saveAudit(canonicalSourceId, "DOCUMENT_CREATED", "system", "Knowledge document created: " + sourceTitle);
        return document;
    }

    @Transactional
    public Optional<KnowledgeDocument> addVersion(UUID documentId, SubmittedDocument file) {
        return knowledgeDocumentRepository.findById(documentId)
                .map(current -> {
                    List<KnowledgeDocumentVersion> versions = new ArrayList<>(current.versions());
                    int nextVersion = current.latestVersion() + 1;
                    versions.add(createVersion(documentId, nextVersion, file, Instant.now(clock)));
                    KnowledgeDocument updated = new KnowledgeDocument(
                            current.documentId(),
                            current.sourceId(),
                            current.sourceTitle(),
                            current.sourceType(),
                            current.authority(),
                            current.allowedAgents(),
                            current.workflowStates(),
                            current.tags(),
                            nextVersion,
                            current.createdAt(),
                            versions
                    );
                    knowledgeDocumentRepository.save(updated);
                    indexVersion(updated, versions.getLast());
                    saveAudit(updated.sourceId(), "DOCUMENT_VERSION_ADDED", "system", "Knowledge document version added: v" + nextVersion);
                    return updated;
                });
    }

    @Transactional
    public KnowledgeSource createSource(
            String sourceId,
            String name,
            String sourceType,
            String authority,
            String ownerTeam,
            int freshnessSlaHours,
            String syncMode,
            String sensitivityPolicy,
            List<String> allowedAgents,
            List<String> workflowStates,
            List<String> tags
    ) {
        Instant now = Instant.now(clock);
        KnowledgeSource source = new KnowledgeSource(
                sourceId,
                name,
                sourceType,
                authority,
                "NEEDS_REVIEW",
                ownerTeam,
                true,
                freshnessSlaHours,
                syncMode,
                sensitivityPolicy,
                List.copyOf(allowedAgents),
                List.copyOf(workflowStates),
                List.copyOf(tags),
                null,
                now.plusSeconds(freshnessSlaHours * 3600L),
                now,
                0,
                0,
                0
        );
        knowledgeDocumentRepository.saveSource(source);
        saveAudit(sourceId, "SOURCE_CREATED", "system", "Knowledge source created: " + name);
        return knowledgeDocumentRepository.findSource(sourceId).orElse(source);
    }

    public List<KnowledgeSource> listSources(Optional<String> status) {
        return knowledgeDocumentRepository.findSources(status);
    }

    public Optional<KnowledgeSource> findSource(String sourceId) {
        return knowledgeDocumentRepository.findSource(sourceId);
    }

    public List<KnowledgeAdapterDescriptor> listAdapters() {
        return knowledgeSourceAdapterRegistry.descriptors();
    }

    @Transactional
    public Optional<KnowledgeSourceConnection> registerConnection(
            String sourceId,
            String adapterType,
            String connectionName,
            String resourceLocator,
            String authType,
            String credentialRef,
            String configJson
    ) {
        KnowledgeSource source = knowledgeDocumentRepository.findSource(sourceId).orElse(null);
        if (source == null) {
            return Optional.empty();
        }
        KnowledgeSourceAdapter adapter = knowledgeSourceAdapterRegistry.find(adapterType)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported knowledge adapter: " + adapterType));
        if (!adapter.descriptor().supportedAuthTypes().contains(authType)) {
            throw new IllegalArgumentException("Unsupported auth type %s for adapter %s".formatted(authType, adapterType));
        }
        Instant now = Instant.now(clock);
        KnowledgeSourceConnection connection = new KnowledgeSourceConnection(
                UUID.randomUUID(),
                sourceId,
                adapterType,
                connectionName,
                "CONFIGURED",
                resourceLocator,
                authType,
                credentialRef == null || credentialRef.isBlank() ? null : credentialRef,
                configJson == null || configJson.isBlank() ? "{}" : configJson,
                null,
                null,
                now
        );
        knowledgeDocumentRepository.saveConnection(connection);
        saveAudit(sourceId, "SOURCE_CONNECTION_REGISTERED", "system", "Registered %s adapter connection: %s".formatted(adapterType, connectionName));
        return Optional.of(connection);
    }

    public List<KnowledgeSourceConnection> connections(String sourceId) {
        return knowledgeDocumentRepository.findConnections(sourceId);
    }

    public Optional<KnowledgeSourceConnection> findConnection(UUID connectionId) {
        return knowledgeDocumentRepository.findConnection(connectionId);
    }

    @Transactional
    public Optional<KnowledgeConnectionCheckResult> checkConnection(UUID connectionId) {
        return knowledgeDocumentRepository.findConnection(connectionId).map(connection -> {
            KnowledgeSourceAdapter adapter = knowledgeSourceAdapterRegistry.find(connection.adapterType())
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported knowledge adapter: " + connection.adapterType()));
            Instant checkedAt = Instant.now(clock);
            KnowledgeConnectionCheckResult result = adapter.checkConnection(connection, checkedAt);
            knowledgeDocumentRepository.updateConnectionStatus(
                    connectionId,
                    result.status(),
                    checkedAt,
                    result.status().equals("CONNECTED") ? null : result.message()
            );
            saveAudit(connection.sourceId(), "SOURCE_CONNECTION_CHECKED", "system", "Connection %s check result: %s".formatted(connection.connectionName(), result.status()));
            return result;
        });
    }

    @Transactional
    public Optional<List<KnowledgeSourceResource>> discoverResources(UUID connectionId) {
        return knowledgeDocumentRepository.findConnection(connectionId).map(connection -> {
            KnowledgeSourceAdapter adapter = knowledgeSourceAdapterRegistry.find(connection.adapterType())
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported knowledge adapter: " + connection.adapterType()));
            List<KnowledgeSourceResource> resources = adapter.discoverResources(connection, Instant.now(clock));
            knowledgeDocumentRepository.saveResources(resources);
            saveAudit(connection.sourceId(), "SOURCE_RESOURCES_DISCOVERED", "system", "Discovered %d resources for %s".formatted(resources.size(), connection.connectionName()));
            return resources;
        });
    }

    public List<KnowledgeSourceResource> resources(UUID connectionId) {
        return knowledgeDocumentRepository.findResources(connectionId);
    }

    public Optional<KnowledgeSourcePayload> fetchResourcePayload(UUID resourceId) {
        return knowledgeDocumentRepository.findResource(resourceId)
                .flatMap(resource -> knowledgeDocumentRepository.findConnection(resource.connectionId())
                        .flatMap(connection -> {
                            KnowledgeSourceAdapter adapter = knowledgeSourceAdapterRegistry.find(connection.adapterType())
                                    .orElseThrow(() -> new IllegalArgumentException("Unsupported knowledge adapter: " + connection.adapterType()));
                            return adapter.fetchResource(connection, resource);
                        }));
    }

    public KnowledgeSummary summary() {
        return knowledgeDocumentRepository.summarize();
    }

    @Transactional
    public Optional<KnowledgeSyncRun> syncSource(String sourceId) {
        return knowledgeDocumentRepository.findSource(sourceId)
                .map(this::syncSource);
    }

    public List<KnowledgeSyncRun> syncRuns(String sourceId) {
        return knowledgeDocumentRepository.findSyncRuns(sourceId);
    }

    public List<KnowledgeAuditEvent> auditEvents(Optional<String> sourceId) {
        return knowledgeDocumentRepository.findAuditEvents(sourceId);
    }

    public List<KnowledgeCitation> search(String query, Optional<String> agentName, Optional<String> workflowState) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<Double> queryEmbedding = knowledgeEmbeddingPort.embed(normalizedQuery);
        return knowledgeDocumentRepository.findLatestChunks().stream()
                .filter(chunk -> agentName.map(agent -> chunk.allowedAgents().contains(agent)).orElse(true))
                .filter(chunk -> workflowState.map(state -> chunk.workflowStates().contains(state)).orElse(true))
                .map(chunk -> new ScoredKnowledgeCitation(chunk, score(chunk, normalizedQuery, queryEmbedding)))
                .filter(scored -> scored.score() > 0.05d)
                .sorted(Comparator.comparingDouble(ScoredKnowledgeCitation::score).reversed())
                .limit(10)
                .map(scored -> new KnowledgeCitation(
                        scored.chunk().sourceId(),
                        scored.chunk().sourceTitle(),
                        scored.chunk().sourceType(),
                        scored.chunk().authority(),
                        scored.chunk().text(),
                        "Knowledge search score %.3f; chunk=%s@v%d:%d; embeddingModel=%s".formatted(
                                scored.score(),
                                scored.chunk().documentId(),
                                scored.chunk().documentVersion(),
                                scored.chunk().chunkIndex(),
                                scored.chunk().embeddingModel()
                        )
                ))
                .toList();
    }

    public List<KnowledgeDocument> listDocuments() {
        return knowledgeDocumentRepository.findAll();
    }

    public Optional<KnowledgeDocument> findDocument(UUID documentId) {
        return knowledgeDocumentRepository.findById(documentId);
    }

    public List<KnowledgeDocument> searchableDocuments() {
        return listDocuments();
    }

    private void ensureSourceForDocument(
            String sourceId,
            String sourceTitle,
            String sourceType,
            String authority,
            List<String> allowedAgents,
            List<String> workflowStates,
            List<String> tags,
            Instant now
    ) {
        KnowledgeSource existing = knowledgeDocumentRepository.findSource(sourceId).orElse(null);
        KnowledgeSource source = new KnowledgeSource(
                sourceId,
                sourceTitle,
                sourceType,
                authority,
                existing == null ? "FRESH" : existing.status(),
                existing == null ? "Unassigned" : existing.ownerTeam(),
                existing == null || existing.enabled(),
                existing == null ? 720 : existing.freshnessSlaHours(),
                existing == null ? "MANUAL_UPLOAD" : existing.syncMode(),
                existing == null ? "INTERNAL" : existing.sensitivityPolicy(),
                List.copyOf(allowedAgents),
                List.copyOf(workflowStates),
                List.copyOf(tags),
                now,
                existing == null ? now.plusSeconds(720L * 3600L) : existing.reviewDueAt(),
                existing == null ? now : existing.createdAt(),
                0,
                0,
                0
        );
        knowledgeDocumentRepository.saveSource(source);
    }

    private KnowledgeDocumentVersion createVersion(UUID documentId, int version, SubmittedDocument file, Instant createdAt) {
        String objectKey = "knowledge/documents/%s/versions/%d/%s".formatted(
                documentId,
                version,
                sanitizeFileName(file.fileName())
        );
        StoredDocument storedDocument = documentStoragePort.store(objectKey, file);
        return new KnowledgeDocumentVersion(
                version,
                file.fileName(),
                file.mediaType(),
                file.sizeBytes(),
                sha256(file.content()),
                storedDocument.bucket(),
                storedDocument.objectKey(),
                storedDocument.storageVersionId(),
                textExtractor.extract(file.fileName(), file.mediaType(), file.content()),
                createdAt
        );
    }

    private void indexVersion(KnowledgeDocument document, KnowledgeDocumentVersion version) {
        knowledgeIndexingPipeline.index(document, version);
    }

    private KnowledgeSyncRun syncSource(KnowledgeSource source) {
        Instant startedAt = Instant.now(clock);
        List<KnowledgeSourceConnection> connections = knowledgeDocumentRepository.findConnections(source.sourceId());

        if (connections.isEmpty()) {
            return syncSourceWithoutConnections(source, startedAt);
        }

        int recordsChanged = 0;
        List<String> failures = new ArrayList<>();
        for (KnowledgeSourceConnection connection : connections) {
            try {
                KnowledgeSourceAdapter adapter = knowledgeSourceAdapterRegistry.find(connection.adapterType())
                        .orElseThrow(() -> new IllegalArgumentException("Unsupported knowledge adapter: " + connection.adapterType()));
                Instant checkedAt = Instant.now(clock);
                KnowledgeConnectionCheckResult checkResult = adapter.checkConnection(connection, checkedAt);
                knowledgeDocumentRepository.updateConnectionStatus(
                        connection.connectionId(),
                        checkResult.status(),
                        checkedAt,
                        checkResult.status().equals("CONNECTED") ? null : checkResult.message()
                );
                if (!checkResult.status().equals("CONNECTED")) {
                    failures.add("%s check failed: %s".formatted(connection.connectionName(), checkResult.message()));
                    continue;
                }

                List<KnowledgeSourceResource> resources = adapter.discoverResources(connection, Instant.now(clock));
                knowledgeDocumentRepository.saveResources(resources);
                for (KnowledgeSourceResource resource : resources) {
                    try {
                        Optional<KnowledgeSourcePayload> payload = adapter.fetchResource(connection, resource);
                        if (payload.isEmpty()) {
                            failures.add("%s has no fetchable content".formatted(resource.title()));
                            continue;
                        }
                        if (ingestSyncedResource(source, connection, resource, payload.get())) {
                            recordsChanged++;
                        }
                    } catch (RuntimeException exception) {
                        failures.add("%s fetch failed: %s".formatted(resource.title(), exception.getMessage()));
                    }
                }
            } catch (RuntimeException exception) {
                failures.add("%s sync failed: %s".formatted(connection.connectionName(), exception.getMessage()));
            }
        }

        Instant completedAt = Instant.now(clock);
        String status = syncStatus(recordsChanged, failures);
        KnowledgeSyncRun syncRun = new KnowledgeSyncRun(
                UUID.randomUUID(),
                source.sourceId(),
                status,
                startedAt,
                completedAt,
                recordsChanged,
                failures.isEmpty() ? null : String.join("; ", failures)
        );
        knowledgeDocumentRepository.saveSyncRun(syncRun);
        knowledgeDocumentRepository.updateSourceSyncState(
                source.sourceId(),
                sourceStatus(status),
                completedAt,
                completedAt.plusSeconds(source.freshnessSlaHours() * 3600L)
        );
        saveAudit(
                source.sourceId(),
                "SOURCE_SYNC_" + status,
                "system",
                "Knowledge source sync %s; recordsChanged=%d; failures=%d".formatted(status, recordsChanged, failures.size())
        );
        return syncRun;
    }

    private KnowledgeSyncRun syncSourceWithoutConnections(KnowledgeSource source, Instant startedAt) {
        Instant completedAt = Instant.now(clock);
        boolean manualSource = "MANUAL_UPLOAD".equals(source.syncMode());
        String status = manualSource ? "SUCCEEDED" : "FAILED";
        String errorMessage = manualSource ? null : "No registered source connections";
        KnowledgeSyncRun syncRun = new KnowledgeSyncRun(
                UUID.randomUUID(),
                source.sourceId(),
                status,
                startedAt,
                completedAt,
                0,
                errorMessage
        );
        knowledgeDocumentRepository.saveSyncRun(syncRun);
        knowledgeDocumentRepository.updateSourceSyncState(
                source.sourceId(),
                manualSource ? "FRESH" : "NEEDS_REVIEW",
                completedAt,
                completedAt.plusSeconds(source.freshnessSlaHours() * 3600L)
        );
        saveAudit(
                source.sourceId(),
                "SOURCE_SYNC_" + status,
                "system",
                manualSource ? "Manual-upload source freshness refreshed" : errorMessage
        );
        return syncRun;
    }

    private boolean ingestSyncedResource(
            KnowledgeSource source,
            KnowledgeSourceConnection connection,
            KnowledgeSourceResource resource,
            KnowledgeSourcePayload payload
    ) {
        SubmittedDocument submittedDocument = new SubmittedDocument(
                payload.fileName(),
                payload.mediaType(),
                payload.content()
        );
        String resourceKey = externalResourceKey(source.sourceId(), connection.connectionId(), resource.externalId());
        String contentHash = sha256(payload.content());
        Optional<KnowledgeSyncedResourceDocument> syncedResource = knowledgeDocumentRepository.findSyncedResourceDocument(resourceKey);
        if (syncedResource.isPresent()) {
            KnowledgeSyncedResourceDocument mapping = syncedResource.get();
            if (contentHash.equals(mapping.lastContentHash())) {
                return false;
            }
            KnowledgeDocument current = knowledgeDocumentRepository.findById(mapping.documentId())
                    .orElseThrow(() -> new IllegalStateException("Synced knowledge document not found: " + mapping.documentId()));
            KnowledgeDocumentVersion latest = current.versions().stream()
                    .filter(version -> version.version() == current.latestVersion())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Knowledge document has no latest version: " + current.documentId()));
            List<KnowledgeDocumentVersion> versions = new ArrayList<>(current.versions());
            int nextVersion = current.latestVersion() + 1;
            KnowledgeDocumentVersion version = createVersion(current.documentId(), nextVersion, submittedDocument, Instant.now(clock));
            versions.add(version);
            KnowledgeDocument updated = new KnowledgeDocument(
                    current.documentId(),
                    current.sourceId(),
                    resource.title(),
                    source.sourceType(),
                    source.authority(),
                    source.allowedAgents(),
                    source.workflowStates(),
                    source.tags(),
                    nextVersion,
                    current.createdAt(),
                    versions
            );
            knowledgeDocumentRepository.save(updated);
            indexVersion(updated, version);
            knowledgeDocumentRepository.saveSyncedResourceDocument(new KnowledgeSyncedResourceDocument(
                    resourceKey,
                    source.sourceId(),
                    connection.connectionId(),
                    resource.externalId(),
                    current.documentId(),
                    contentHash,
                    Instant.now(clock)
            ));
            saveAudit(source.sourceId(), "SYNCED_DOCUMENT_VERSION_ADDED", "system", "Synced new version for resource: " + resource.title());
            return true;
        }

        UUID documentId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        KnowledgeDocumentVersion version = createVersion(documentId, 1, submittedDocument, now);
        KnowledgeDocument document = new KnowledgeDocument(
                documentId,
                source.sourceId(),
                resource.title(),
                source.sourceType(),
                source.authority(),
                source.allowedAgents(),
                source.workflowStates(),
                source.tags(),
                1,
                now,
                new ArrayList<>(List.of(version))
        );
        knowledgeDocumentRepository.save(document);
        indexVersion(document, version);
        knowledgeDocumentRepository.saveSyncedResourceDocument(new KnowledgeSyncedResourceDocument(
                resourceKey,
                source.sourceId(),
                connection.connectionId(),
                resource.externalId(),
                documentId,
                contentHash,
                Instant.now(clock)
        ));
        saveAudit(source.sourceId(), "SYNCED_DOCUMENT_CREATED", "system", "Synced new resource: " + resource.title());
        return true;
    }

    private String externalResourceKey(String sourceId, UUID connectionId, String externalId) {
        String naturalKey = "%s:%s:%s".formatted(sourceId, connectionId, externalId);
        return sha256(naturalKey.getBytes(StandardCharsets.UTF_8));
    }

    private String syncStatus(int recordsChanged, List<String> failures) {
        if (failures.isEmpty()) {
            return "SUCCEEDED";
        }
        return recordsChanged > 0 ? "PARTIAL" : "FAILED";
    }

    private String sourceStatus(String syncStatus) {
        return syncStatus.equals("SUCCEEDED") ? "FRESH" : "NEEDS_REVIEW";
    }

    private void backfillMissingChunks() {
        for (KnowledgeDocument document : knowledgeDocumentRepository.findAll()) {
            for (KnowledgeDocumentVersion version : document.versions()) {
                indexVersion(document, version);
            }
        }
    }

    private void saveAudit(String sourceId, String action, String actor, String details) {
        knowledgeDocumentRepository.saveAuditEvent(new KnowledgeAuditEvent(
                UUID.randomUUID(),
                sourceId,
                action,
                actor,
                details,
                Instant.now(clock)
        ));
    }

    private double score(com.aegisflow.api.infrastructure.knowledge.KnowledgeChunk chunk, String query, List<Double> queryEmbedding) {
        double score = cosine(queryEmbedding, chunk.embedding());
        for (String tag : chunk.tags()) {
            if (query.contains(tag.toLowerCase(Locale.ROOT))) {
                score += 0.08d;
            }
        }
        if (chunk.sourceTitle().toLowerCase(Locale.ROOT).contains(query)) {
            score += 0.12d;
        }
        return score;
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left.isEmpty() || right.isEmpty() || left.size() != right.size()) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftMagnitude = 0.0d;
        double rightMagnitude = 0.0d;
        for (int index = 0; index < left.size(); index++) {
            dot += left.get(index) * right.get(index);
            leftMagnitude += left.get(index) * left.get(index);
            rightMagnitude += right.get(index) * right.get(index);
        }
        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private record ScoredKnowledgeCitation(
            com.aegisflow.api.infrastructure.knowledge.KnowledgeChunk chunk,
            double score
    ) {
    }
}
