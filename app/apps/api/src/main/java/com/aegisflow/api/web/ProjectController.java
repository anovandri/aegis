package com.aegisflow.api.web;

import com.aegisflow.api.application.ProjectLifecycleStarter;
import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.SubmittedDocument;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.Project;
import com.aegisflow.api.domain.ProjectContext;
import com.aegisflow.api.workflow.ProjectWorkflowProgress;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectLifecycleStarter projectLifecycleStarter;

    public ProjectController(ProjectService projectService, ProjectLifecycleStarter projectLifecycleStarter) {
        this.projectService = projectService;
        this.projectLifecycleStarter = projectLifecycleStarter;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Project submitBrd(
            @RequestParam @NotBlank String name,
            @RequestParam @NotBlank String businessOwner,
            @RequestParam @NotBlank String domain,
            @RequestParam("brdFile") MultipartFile brdFile
    ) {
        Project project = projectService.submitBrd(
                name,
                businessOwner,
                domain,
                toSubmittedDocument(brdFile)
        );
        projectLifecycleStarter.start(project.projectId());
        return project;
    }

    @PostMapping(value = "/{projectId}/brd-versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ArtifactVersion submitBrdVersion(
            @PathVariable UUID projectId,
            @RequestParam("brdFile") MultipartFile brdFile
    ) {
        return projectService.submitBrdVersion(projectId, toSubmittedDocument(brdFile))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @GetMapping("/{projectId}")
    Project getProject(@PathVariable UUID projectId) {
        return projectService.findProject(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @GetMapping("/{projectId}/context")
    ProjectContext getProjectContext(@PathVariable UUID projectId) {
        return projectService.assembleContext(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @GetMapping("/{projectId}/workflow")
    ProjectWorkflowProgress getWorkflowProgress(@PathVariable UUID projectId) {
        projectService.findProject(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        return projectLifecycleStarter.getProgress(projectId);
    }

    private SubmittedDocument toSubmittedDocument(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BRD file is required");
        }

        try {
            return new SubmittedDocument(
                    file.getOriginalFilename() == null ? "brd" : file.getOriginalFilename(),
                    file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BRD file could not be read", exception);
        }
    }
}
