package com.aegisflow.api.infrastructure.knowledge;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class KnowledgeSourceAdapterRegistry {
    private final Map<String, KnowledgeSourceAdapter> adapters;

    public KnowledgeSourceAdapterRegistry(List<KnowledgeSourceAdapter> adapters) {
        this.adapters = adapters.stream()
                .collect(Collectors.toUnmodifiableMap(KnowledgeSourceAdapter::adapterType, Function.identity()));
    }

    public List<KnowledgeAdapterDescriptor> descriptors() {
        return adapters.values().stream()
                .map(KnowledgeSourceAdapter::descriptor)
                .sorted(Comparator.comparing(KnowledgeAdapterDescriptor::displayName))
                .toList();
    }

    public Optional<KnowledgeSourceAdapter> find(String adapterType) {
        return Optional.ofNullable(adapters.get(adapterType));
    }
}
