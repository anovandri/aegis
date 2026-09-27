package com.aegisflow.api.infrastructure.project;

import com.aegisflow.api.domain.AgentExecution;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.Project;
import com.aegisflow.api.domain.WorkflowState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {
    void saveProject(Project project);

    Optional<Project> findProject(UUID projectId);

    void updateProjectState(UUID projectId, WorkflowState nextState);

    void saveArtifactVersion(UUID projectId, ArtifactVersion artifactVersion);

    List<ArtifactVersion> findArtifactVersions(UUID projectId);

    Optional<ArtifactVersion> latestArtifact(UUID projectId, ArtifactType artifactType);

    void saveAgentExecution(AgentExecution execution);
}
