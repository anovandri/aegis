package com.aegisflow.api.infrastructure.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class GoogleDriveKnowledgeSourceAdapter implements KnowledgeSourceAdapter {
    private static final String DEFAULT_BASE_URL = "https://www.googleapis.com/drive/v3";

    private final CredentialResolverPort credentialResolver;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GoogleDriveKnowledgeSourceAdapter(CredentialResolverPort credentialResolver, ObjectMapper objectMapper) {
        this.credentialResolver = credentialResolver;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public KnowledgeAdapterDescriptor descriptor() {
        return ConfiguredKnowledgeSourceAdapter.googleDrive().descriptor();
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
                    .uri(filesListUri(config, 1))
                    .headers(headers -> headers.setBearerAuth(token.get()))
                    .retrieve()
                    .body(JsonNode.class);
            return new KnowledgeConnectionCheckResult("CONNECTED", "Google Drive folder is reachable with read-only credentials.", checkedAt);
        } catch (RestClientException exception) {
            return new KnowledgeConnectionCheckResult("FAILED", "Google Drive connection failed: " + exception.getMessage(), checkedAt);
        }
    }

    @Override
    public List<KnowledgeSourceResource> discoverResources(KnowledgeSourceConnection connection, Instant discoveredAt) {
        AdapterConfig config = parseConfig(connection);
        String token = credentialResolver.resolve(connection.credentialRef())
                .orElseThrow(() -> new IllegalStateException("Credential reference could not be resolved: " + connection.credentialRef()));
        JsonNode response = restClient.get()
                .uri(filesListUri(config, config.maxResources()))
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(JsonNode.class);

        List<KnowledgeSourceResource> resources = new ArrayList<>();
        if (response == null) {
            return resources;
        }
        for (JsonNode file : response.path("files")) {
            String fileId = file.path("id").asText();
            String mimeType = file.path("mimeType").asText();
            resources.add(new KnowledgeSourceResource(
                    UUID.randomUUID(),
                    connection.connectionId(),
                    fileId,
                    resourceType(mimeType),
                    file.path("name").asText(fileId),
                    file.path("webViewLink").asText("https://drive.google.com/file/d/" + fileId),
                    file.path("modifiedTime").asText(null),
                    file.path("md5Checksum").asText(null),
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
        byte[] content;
        String mediaType;
        if (resource.resourceType().startsWith("GOOGLE_WORKSPACE_")) {
            mediaType = exportMediaType(resource.resourceType());
            content = restClient.get()
                    .uri("%s/files/%s/export?mimeType=%s".formatted(
                            config.baseUrl(),
                            encode(resource.externalId()),
                            encode(mediaType)))
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(byte[].class);
        } else {
            mediaType = "application/octet-stream";
            content = restClient.get()
                    .uri("%s/files/%s?alt=media".formatted(config.baseUrl(), encode(resource.externalId())))
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(byte[].class);
        }
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(new KnowledgeSourcePayload(
                resource.title(),
                mediaType,
                content,
                resource.versionRef()
        ));
    }

    private AdapterConfig parseConfig(KnowledgeSourceConnection connection) {
        try {
            JsonNode node = objectMapper.readTree(connection.configJson());
            String folderId = optional(node, "folderId").orElse(connection.resourceLocator());
            String baseUrl = optional(node, "baseUrl").orElse(DEFAULT_BASE_URL);
            String query = optional(node, "query").orElse("");
            int maxResources = optional(node, "maxResources")
                    .map(Integer::parseInt)
                    .orElse(50);
            return new AdapterConfig(trimTrailingSlash(baseUrl), folderId, query, maxResources);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Google Drive adapter config JSON", exception);
        }
    }

    private String driveQuery(String folderId, String extraQuery) {
        String baseQuery = "'%s' in parents and trashed = false".formatted(folderId.replace("'", "\\'"));
        return extraQuery == null || extraQuery.isBlank() ? baseQuery : baseQuery + " and " + extraQuery;
    }

    private URI filesListUri(AdapterConfig config, int pageSize) {
        return URI.create("%s/files?q=%s&fields=%s&pageSize=%d&supportsAllDrives=true&includeItemsFromAllDrives=true".formatted(
                config.baseUrl(),
                encode(driveQuery(config.folderId(), config.query())),
                encode("files(id,name,mimeType,modifiedTime,webViewLink,md5Checksum)"),
                pageSize
        ));
    }

    private String resourceType(String mimeType) {
        return switch (mimeType) {
            case "application/vnd.google-apps.document" -> "GOOGLE_WORKSPACE_DOCUMENT";
            case "application/vnd.google-apps.spreadsheet" -> "GOOGLE_WORKSPACE_SPREADSHEET";
            case "application/vnd.google-apps.presentation" -> "GOOGLE_WORKSPACE_PRESENTATION";
            case "application/pdf" -> "PDF";
            default -> "DOCUMENT";
        };
    }

    private String exportMediaType(String resourceType) {
        return switch (resourceType) {
            case "GOOGLE_WORKSPACE_SPREADSHEET" -> "text/csv";
            case "GOOGLE_WORKSPACE_PRESENTATION" -> "text/plain";
            default -> "text/plain";
        };
    }

    private Optional<String> optional(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record AdapterConfig(
            String baseUrl,
            String folderId,
            String query,
            int maxResources
    ) {
    }
}
