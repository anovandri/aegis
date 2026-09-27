package com.aegisflow.api.infrastructure.temporal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aegisflow.temporal")
public record TemporalProperties(
        boolean enabled,
        String target,
        String namespace,
        String taskQueue
) {
}
