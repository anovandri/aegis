package com.aegisflow.api.infrastructure.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class GitLabKnowledgeSourceAdapter implements KnowledgeSourceAdapter {
    private final CredentialResolverPort credentialResolver;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GitLabKnowledgeSourceAdapter(CredentialResolverPort credentialResolver, ObjectMapper objectMapper) {
        this.credentialResolver = credentialResolver;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public KnowledgeAdapterDescriptor descriptor() {
        return ConfiguredKnowledgeSourceAdapter.gitLab().descriptor();
    }

    @Override
    public KnowledgeConnectionCheckResult checkConnection(KnowledgeSourceConnection connection, Instant checkedAt) {
        AdapterConfig config = parseConfig(connection);
        Optional<String> token = credentialResolver.resolve(connection.credentialRef());
        if (token.isEmpty()) {
            return new KnowledgeConnectionCheckResult("FAILED", "Credential reference could not be resolved. Use env://VARIABLE_NAME for local development.", checkedAt);
        }

        try {
            restClient.get()
                    .uri("%s/api/v4/projects/%s".formatted(config.baseUrl(), encodePathSegment(config.projectPath())))
                    .headers(headers -> applyAuth(headers, connection.authType(), token.get()))
                    .retrieve()
                    .body(String.class);
            return new KnowledgeConnectionCheckResult("CONNECTED", "GitLab project is reachable with read-only credentials.", checkedAt);
        } catch (RestClientException exception) {
            return new KnowledgeConnectionCheckResult("FAILED", "GitLab connection failed: " + exception.getMessage(), checkedAt);
        }
    }

    @Override
    public List<KnowledgeSourceResource> discoverResources(KnowledgeSourceConnection connection, Instant discoveredAt) {
        AdapterConfig config = parseConfig(connection);
        String token = credentialResolver.resolve(connection.credentialRef())
                .orElseThrow(() -> new IllegalStateException("Credential reference could not be resolved: " + connection.credentialRef()));
        JsonNode response = restClient.get()
                .uri(URI.create("%s/api/v4/projects/%s/repository/tree?path=%s&ref=%s&recursive=true&per_page=%d".formatted(
                        config.baseUrl(),
                        encodePathSegment(config.projectPath()),
                        encodeQueryValue(config.path()),
                        encodeQueryValue(config.ref()),
                        config.maxResources())))
                .headers(headers -> applyAuth(headers, connection.authType(), token))
                .retrieve()
                .body(JsonNode.class);

        List<KnowledgeSourceResource> resources = new ArrayList<>();
        if (response == null || !response.isArray()) {
            return resources;
        }
        for (JsonNode node : response) {
            if (!"blob".equals(node.path("type").asText())) {
                continue;
            }
            String path = node.path("path").asText();
            resources.add(new KnowledgeSourceResource(
                    UUID.randomUUID(),
                    connection.connectionId(),
                    path,
                    "REPOSITORY_FILE",
                    path,
                    "%s/%s/-/blob/%s/%s".formatted(config.baseUrl(), config.projectPath(), config.ref(), path),
                    config.ref(),
                    node.path("id").asText(null),
                    "DISCOVERED",
                    discoveredAt
            ));
        }
        return resources;
    }

    @Override
    public Optional<KnowledgeSourcePayload> fetchResource(KnowledgeSourceConnection connection, KnowledgeSourceResource resource) {
        AdapterConfig config = parseConfig(connection);
        String token = credentialResolver.resolve(connection.credentialRef())
                .orElseThrow(() -> new IllegalStateException("Credential reference could not be resolved: " + connection.credentialRef()));
        byte[] content = restClient.get()
                .uri("%s/api/v4/projects/%s/repository/files/%s/raw?ref=%s".formatted(
                        config.baseUrl(),
                        encodePathSegment(config.projectPath()),
                        encodePathSegment(resource.externalId()),
                        encodeQueryValue(config.ref())))
                .headers(headers -> applyAuth(headers, connection.authType(), token))
                .retrieve()
                .body(byte[].class);
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(new KnowledgeSourcePayload(
                fileName(resource.externalId()),
                mediaType(resource.externalId()),
                content,
                resource.versionRef()
        ));
    }

    private AdapterConfig parseConfig(KnowledgeSourceConnection connection) {
        try {
            JsonNode node = objectMapper.readTree(connection.configJson());
            String baseUrl = required(node, "baseUrl");
            String projectPath = optional(node, "projectPath").orElse(connection.resourceLocator());
            String ref = optional(node, "ref").orElse("main");
            String path = optional(node, "path").orElse("");
            int maxResources = optional(node, "maxResources")
                    .map(Integer::parseInt)
                    .orElse(50);
            return new AdapterConfig(trimTrailingSlash(baseUrl), projectPath, ref, path, maxResources);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid GitLab adapter config JSON", exception);
        }
    }

    private void applyAuth(HttpHeaders headers, String authType, String token) {
        if ("OAUTH".equals(authType)) {
            headers.setBearerAuth(token);
        } else {
            headers.set("PRIVATE-TOKEN", token);
        }
    }

    private String required(JsonNode node, String fieldName) {
        return optional(node, fieldName)
                .orElseThrow(() -> new IllegalArgumentException("Missing GitLab config field: " + fieldName));
    }

    private Optional<String> optional(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String fileName(String path) {
        int slashIndex = path.lastIndexOf('/');
        return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }

    private String mediaType(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "application/yaml";
        }
        return "text/plain";
    }

    private record AdapterConfig(
            String baseUrl,
            String projectPath,
            String ref,
            String path,
            int maxResources
    ) {
    }
}
