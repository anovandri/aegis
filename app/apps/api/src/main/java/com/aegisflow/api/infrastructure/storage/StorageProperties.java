package com.aegisflow.api.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aegisflow.storage")
public record StorageProperties(
        String provider,
        String bucket,
        String endpoint,
        String accessKey,
        String secretKey
) {
}
