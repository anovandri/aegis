package com.aegisflow.api.infrastructure.knowledge;

import java.util.List;

public record KnowledgeAdapterDescriptor(
        String adapterType,
        String displayName,
        String description,
        List<String> supportedAuthTypes,
        List<String> supportedResourceTypes,
        List<String> requiredConfigKeys,
        boolean implemented
) {
    public KnowledgeAdapterDescriptor {
        supportedAuthTypes = supportedAuthTypes == null ? List.of() : List.copyOf(supportedAuthTypes);
        supportedResourceTypes = supportedResourceTypes == null ? List.of() : List.copyOf(supportedResourceTypes);
        requiredConfigKeys = requiredConfigKeys == null ? List.of() : List.copyOf(requiredConfigKeys);
    }
}
