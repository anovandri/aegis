package com.aegisflow.api.web;

import com.aegisflow.api.application.projectroom.ProjectRoomReadModelService;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectMessage;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoom;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomActivityEvent;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomArtifact;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
public class ProjectRoomController {
    private final ProjectRoomReadModelService projectRoomService;

    public ProjectRoomController(ProjectRoomReadModelService projectRoomService) {
        this.projectRoomService = projectRoomService;
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
}
