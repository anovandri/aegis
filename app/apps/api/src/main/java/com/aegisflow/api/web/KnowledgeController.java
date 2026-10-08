package com.aegisflow.api.web;

import com.aegisflow.api.application.KnowledgeService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeDocument;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeDocumentVersion;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeAdapterDescriptor;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeAuditEvent;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeConnectionCheckResult;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSource;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceConnection;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourcePayload;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourcePreview;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSourceResource;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSummary;
import com.aegisflow.api.infrastructure.knowledge.KnowledgeSyncRun;
import com.aegisflow.api.ports.KnowledgeCitation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    KnowledgeDocument createDocument(
            @RequestParam @NotBlank String sourceTitle,
            @RequestParam(required = false) String sourceId,
            @RequestParam @NotBlank String sourceType,
            @RequestParam @NotBlank String authority,
            @RequestParam @NotBlank String allowedAgents,
            @RequestParam @NotBlank String workflowStates,
            @RequestParam(required = false, defaultValue = "") String tags,
            @RequestParam("file") MultipartFile file
    ) {
        return knowledgeService.createDocument(
                sourceTitle,
                sourceId,
                sourceType,
                authority,
                splitCsv(allowedAgents),
                splitCsv(workflowStates),
                splitCsv(tags),
                toSubmittedDocument(file)
        );
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    KnowledgeSource createSource(@Valid @RequestBody CreateKnowledgeSourceRequest request) {
        return knowledgeService.createSource(
                request.sourceId(),
                request.name(),
                request.sourceType(),
                request.authority(),
                request.ownerTeam(),
                request.freshnessSlaHours(),
                request.syncMode(),
                request.sensitivityPolicy(),
                request.allowedAgents(),
                request.workflowStates(),
                request.tags()
        );
    }

    @GetMapping("/summary")
    KnowledgeSummary summary() {
        return knowledgeService.summary();
    }

    @GetMapping("/sources")
    List<KnowledgeSource> listSources(@RequestParam(required = false) String status) {
        return knowledgeService.listSources(Optional.ofNullable(status));
    }

    @GetMapping("/adapters")
    List<KnowledgeAdapterDescriptor> listAdapters() {
        return knowledgeService.listAdapters();
    }

    @GetMapping("/sources/{sourceId}")
    KnowledgeSource getSource(@PathVariable String sourceId) {
        return knowledgeService.findSource(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source not found"));
    }

    @GetMapping("/sources/{sourceId}/preview")
    KnowledgeSourcePreview sourcePreview(@PathVariable String sourceId) {
        return knowledgeService.previewSource(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source not found"));
    }

    @PostMapping("/sources/{sourceId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    KnowledgeSyncRun syncSource(@PathVariable String sourceId) {
        return knowledgeService.syncSource(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source not found"));
    }

    @GetMapping("/sources/{sourceId}/sync-runs")
    List<KnowledgeSyncRun> syncRuns(@PathVariable String sourceId) {
        knowledgeService.findSource(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source not found"));
        return knowledgeService.syncRuns(sourceId);
    }

    @PostMapping("/sources/{sourceId}/connections")
    @ResponseStatus(HttpStatus.CREATED)
    KnowledgeSourceConnection registerConnection(
            @PathVariable String sourceId,
            @Valid @RequestBody RegisterKnowledgeSourceConnectionRequest request
    ) {
        try {
            return knowledgeService.registerConnection(
                            sourceId,
                            request.adapterType(),
                            request.connectionName(),
                            request.resourceLocator(),
                            request.authType(),
                            request.credentialRef(),
                            request.configJson()
                    )
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/sources/{sourceId}/connections")
    List<KnowledgeSourceConnection> connections(@PathVariable String sourceId) {
        knowledgeService.findSource(sourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source not found"));
        return knowledgeService.connections(sourceId);
    }

    @GetMapping("/connections/{connectionId}")
    KnowledgeSourceConnection getConnection(@PathVariable UUID connectionId) {
        return knowledgeService.findConnection(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source connection not found"));
    }

    @PostMapping("/connections/{connectionId}/check")
    KnowledgeConnectionCheckResult checkConnection(@PathVariable UUID connectionId) {
        try {
            return knowledgeService.checkConnection(connectionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source connection not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/connections/{connectionId}/discover")
    List<KnowledgeSourceResource> discoverResources(@PathVariable UUID connectionId) {
        try {
            return knowledgeService.discoverResources(connectionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source connection not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/connections/{connectionId}/resources")
    List<KnowledgeSourceResource> resources(@PathVariable UUID connectionId) {
        knowledgeService.findConnection(connectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source connection not found"));
        return knowledgeService.resources(connectionId);
    }

    @GetMapping("/resources/{resourceId}/content")
    KnowledgeResourceContentResponse resourceContent(@PathVariable UUID resourceId) {
        try {
            KnowledgeSourcePayload payload = knowledgeService.fetchResourcePayload(resourceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge source resource not found or has no fetchable content"));
            return new KnowledgeResourceContentResponse(
                    payload.fileName(),
                    payload.mediaType(),
                    payload.versionRef(),
                    Base64.getEncoder().encodeToString(payload.content())
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/audit")
    List<KnowledgeAuditEvent> auditEvents(@RequestParam(required = false) String sourceId) {
        return knowledgeService.auditEvents(Optional.ofNullable(sourceId));
    }

    @GetMapping("/search")
    List<KnowledgeCitation> search(
            @RequestParam @NotBlank String q,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String workflowState
    ) {
        return knowledgeService.search(q, Optional.ofNullable(agentName), Optional.ofNullable(workflowState));
    }

    @GetMapping("/documents")
    List<KnowledgeDocument> listDocuments() {
        return knowledgeService.listDocuments();
    }

    @GetMapping("/documents/{documentId}")
    KnowledgeDocument getDocument(@PathVariable UUID documentId) {
        return knowledgeService.findDocument(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found"));
    }

    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    KnowledgeDocument addVersion(
            @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file
    ) {
        return knowledgeService.addVersion(documentId, toSubmittedDocument(file))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found"));
    }

    @GetMapping("/documents/{documentId}/versions")
    List<KnowledgeDocumentVersion> listVersions(@PathVariable UUID documentId) {
        return knowledgeService.findDocument(documentId)
                .map(KnowledgeDocument::versions)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Knowledge document not found"));
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private SubmittedDocument toSubmittedDocument(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge file is required");
        }

        try {
            return new SubmittedDocument(
                    file.getOriginalFilename() == null ? "knowledge-document" : file.getOriginalFilename(),
                    file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Knowledge file could not be read", exception);
        }
    }

    record CreateKnowledgeSourceRequest(
            @NotBlank String sourceId,
            @NotBlank String name,
            @NotBlank String sourceType,
            @NotBlank String authority,
            @NotBlank String ownerTeam,
            @Positive int freshnessSlaHours,
            @NotBlank String syncMode,
            @NotBlank String sensitivityPolicy,
            List<String> allowedAgents,
            List<String> workflowStates,
            List<String> tags
    ) {
        CreateKnowledgeSourceRequest {
            allowedAgents = allowedAgents == null ? List.of() : List.copyOf(allowedAgents);
            workflowStates = workflowStates == null ? List.of() : List.copyOf(workflowStates);
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    record RegisterKnowledgeSourceConnectionRequest(
            @NotBlank String adapterType,
            @NotBlank String connectionName,
            @NotBlank String resourceLocator,
            @NotBlank String authType,
            String credentialRef,
            String configJson
    ) {
    }

    record KnowledgeResourceContentResponse(
            String fileName,
            String mediaType,
            String versionRef,
            String contentBase64
    ) {
    }
}
