package com.aegisflow.api.web;

import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.application.projectroom.ProjectRoomCommandService;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModelService;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectMessage;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoom;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomActivityEvent;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomArtifact;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomArtifactDetail;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.HumanReviewAttachment;
import com.aegisflow.api.domain.HumanReviewDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
public class ProjectRoomController {
    private final ProjectRoomReadModelService projectRoomService;
    private final ProjectRoomCommandService projectRoomCommandService;

    public ProjectRoomController(ProjectRoomReadModelService projectRoomService, ProjectRoomCommandService projectRoomCommandService) {
        this.projectRoomService = projectRoomService;
        this.projectRoomCommandService = projectRoomCommandService;
    }

    @GetMapping({"/api/project-rooms/{projectId}", "/api/projects/{projectId}/room"})
    ProjectRoom projectRoom(@PathVariable UUID projectId) {
        return projectRoomService.projectRoom(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @GetMapping({"/api/project-rooms/{projectId}/artifacts", "/api/projects/{projectId}/artifacts"})
    List<ProjectRoomArtifact> artifacts(@PathVariable UUID projectId) {
        return projectRoomService.artifactsIfProjectExists(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @GetMapping("/api/project-rooms/{projectId}/artifacts/{artifactId}/versions/{version}")
    ProjectRoomArtifactDetail artifactDetail(
            @PathVariable UUID projectId,
            @PathVariable UUID artifactId,
            @PathVariable int version
    ) {
        return projectRoomService.artifactDetail(projectId, artifactId, version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact version not found"));
    }

    @GetMapping("/api/project-rooms/{projectId}/artifacts/{artifactId}/versions/{version}/content")
    ResponseEntity<byte[]> artifactContent(
            @PathVariable UUID projectId,
            @PathVariable UUID artifactId,
            @PathVariable int version
    ) {
        var artifactContent = projectRoomService.artifactContent(projectId, artifactId, version)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact version not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifactContent.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(artifactContent.fileName()))
                .body(artifactContent.content());
    }

    @GetMapping({"/api/project-rooms/{projectId}/activity", "/api/projects/{projectId}/activity"})
    List<ProjectRoomActivityEvent> activity(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return projectRoomService.activityIfProjectExists(projectId, boundedLimit(limit))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @GetMapping({"/api/project-rooms/{projectId}/messages", "/api/projects/{projectId}/messages"})
    List<ProjectMessage> messages(@PathVariable UUID projectId) {
        return projectRoomService.messagesIfProjectExists(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @PostMapping({"/api/project-rooms/{projectId}/messages", "/api/projects/{projectId}/messages"})
    ProjectMessage addMessage(
            @PathVariable UUID projectId,
            Authentication authentication,
            @Valid @RequestBody AddProjectMessageRequest request
    ) {
        try {
            return projectRoomService.addProjectMessage(
                            projectId,
                            actorName(authentication, request.actor()),
                            request.body(),
                            request.anchorType(),
                            request.anchorId()
                    )
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/api/project-rooms/{projectId}/technical-review/decision")
    HumanReviewDecision decideTechnicalReview(
            @PathVariable UUID projectId,
            Authentication authentication,
            @Valid @RequestBody SubmitTechnicalReviewDecisionRequest request
    ) {
        try {
            return projectRoomCommandService.decideTechnicalReview(
                            projectId,
                            request.decision(),
                            actorName(authentication, request.actor()),
                            request.correction(),
                            request.clarificationQuestions(),
                            request.rejectionReason()
                    )
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Open technical review not found"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping(value = "/api/project-rooms/{projectId}/reviews/{reviewId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    HumanReviewAttachment uploadReviewAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID reviewId,
            Authentication authentication,
            @RequestParam @NotBlank String actor,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            return projectRoomCommandService.uploadReviewAttachment(
                            projectId,
                            reviewId,
                            actorName(authentication, actor),
                            toSubmittedDocument(file, "Attachment file is required"),
                            description
                    )
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found for project"));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping(value = "/api/project-rooms/{projectId}/brd-versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ArtifactVersion uploadRevisedBrd(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "false") boolean restartWorkflow,
            @RequestParam("brdFile") MultipartFile brdFile
    ) {
        return projectRoomCommandService.uploadRevisedBrd(
                        projectId,
                        toSubmittedDocument(brdFile, "BRD file is required"),
                        restartWorkflow
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @GetMapping("/api/project-rooms/{projectId}/suggested-actions")
    List<String> suggestedActions(@PathVariable UUID projectId) {
        List<String> actions = projectRoomService.suggestedProjectRoomActions(projectId);
        if (actions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found");
        }
        return actions;
    }

    private int boundedLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            return 1;
        }
        return Math.min(requestedLimit, 500);
    }

    private String actorName(Authentication authentication, String fallbackActor) {
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return authentication.getName();
        }
        return fallbackActor;
    }

    record AddProjectMessageRequest(
            @NotBlank String actor,
            @NotBlank String body,
            String anchorType,
            String anchorId
    ) {
    }

    record SubmitTechnicalReviewDecisionRequest(
            @NotNull HumanDecision decision,
            @NotBlank String actor,
            String correction,
            List<String> clarificationQuestions,
            String rejectionReason
    ) {
        SubmitTechnicalReviewDecisionRequest {
            clarificationQuestions = clarificationQuestions == null ? List.of() : List.copyOf(clarificationQuestions);
        }
    }

    private SubmittedDocument toSubmittedDocument(MultipartFile file, String requiredMessage) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, requiredMessage);
        }

        try {
            return new SubmittedDocument(
                    file.getOriginalFilename() == null ? "document" : file.getOriginalFilename(),
                    file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File could not be read", exception);
        }
    }
}
