package com.aegisflow.api.infrastructure.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KnowledgeSourceAdapter {
    KnowledgeAdapterDescriptor descriptor();

    default String adapterType() {
        return descriptor().adapterType();
    }

    default KnowledgeConnectionCheckResult checkConnection(KnowledgeSourceConnection connection, Instant checkedAt) {
        if (connection.resourceLocator() == null || connection.resourceLocator().isBlank()) {
            return new KnowledgeConnectionCheckResult("FAILED", "Resource locator is required.", checkedAt);
        }
        if (requiresCredential() && (connection.credentialRef() == null || connection.credentialRef().isBlank())) {
            return new KnowledgeConnectionCheckResult("FAILED", "Credential reference is required. Store secrets outside AegisFlow and provide only the reference.", checkedAt);
        }
        return new KnowledgeConnectionCheckResult("CONNECTED", "Connector configuration is valid for discovery.", checkedAt);
    }

    default List<KnowledgeSourceResource> discoverResources(KnowledgeSourceConnection connection, Instant discoveredAt) {
        return List.of(new KnowledgeSourceResource(
                java.util.UUID.randomUUID(),
                connection.connectionId(),
                connection.adapterType() + ":" + connection.resourceLocator(),
                descriptor().supportedResourceTypes().isEmpty() ? "RESOURCE" : descriptor().supportedResourceTypes().getFirst(),
                connection.connectionName(),
                connection.resourceLocator(),
                null,
                null,
                "DISCOVERED",
                discoveredAt
        ));
    }

    default Optional<KnowledgeSourcePayload> fetchResource(KnowledgeSourceConnection connection, KnowledgeSourceResource resource) {
        return Optional.empty();
    }

    private boolean requiresCredential() {
        return descriptor().supportedAuthTypes().stream()
                .noneMatch(authType -> authType.equals("NONE"));
    }
}
