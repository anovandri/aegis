package com.aegisflow.api.application;

import com.aegisflow.api.domain.AgentExecution;
import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.Project;
import com.aegisflow.api.domain.ProjectContext;
import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.infrastructure.project.ProjectRepository;
import com.aegisflow.api.ports.DocumentStoragePort;
import com.aegisflow.api.ports.StoredDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectService {
    private final Clock clock;
    private final DocumentStoragePort documentStoragePort;
    private final ProjectRepository projectRepository;

    public ProjectService(Clock clock, DocumentStoragePort documentStoragePort, ProjectRepository projectRepository) {
        this.clock = clock;
        this.documentStoragePort = documentStoragePort;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project submitBrd(String name, String businessOwner, String domain, SubmittedDocument brdDocument) {
        UUID projectId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        Project project = new Project(projectId, name, businessOwner, domain, WorkflowState.BRD_SUBMITTED, now);
        UUID artifactId = UUID.randomUUID();
        ArtifactVersion brd = createArtifactVersion(projectId, artifactId, 1, brdDocument, now);

        projectRepository.saveProject(project);
        projectRepository.saveArtifactVersion(projectId, brd);

        return project;
    }

    @Transactional
    public Optional<ArtifactVersion> submitBrdVersion(UUID projectId, SubmittedDocument brdDocument) {
        if (projectRepository.findProject(projectId).isEmpty()) {
            return Optional.empty();
        }

        List<ArtifactVersion> projectArtifacts = projectRepository.findArtifactVersions(projectId);
        int nextVersion = projectArtifacts.stream()
                .filter(artifact -> artifact.artifactType() == ArtifactType.BRD)
                .mapToInt(ArtifactVersion::version)
                .max()
                .orElse(0) + 1;
        UUID artifactId = projectArtifacts.stream()
                .filter(artifact -> artifact.artifactType() == ArtifactType.BRD)
                .findFirst()
                .map(ArtifactVersion::artifactId)
                .orElseGet(UUID::randomUUID);
        ArtifactVersion brd = createArtifactVersion(projectId, artifactId, nextVersion, brdDocument, Instant.now(clock));

        projectRepository.saveArtifactVersion(projectId, brd);
        return Optional.of(brd);
    }

    @Transactional
    public Optional<ArtifactVersion> addGeneratedArtifact(
            UUID projectId,
            ArtifactType artifactType,
            String fileName,
            String mediaType,
            byte[] content
    ) {
        if (projectRepository.findProject(projectId).isEmpty()) {
            return Optional.empty();
        }

        List<ArtifactVersion> projectArtifacts = projectRepository.findArtifactVersions(projectId);
        int nextVersion = projectArtifacts.stream()
                .filter(artifact -> artifact.artifactType() == artifactType)
                .mapToInt(ArtifactVersion::version)
                .max()
                .orElse(0) + 1;
        UUID artifactId = projectArtifacts.stream()
                .filter(artifact -> artifact.artifactType() == artifactType)
                .findFirst()
                .map(ArtifactVersion::artifactId)
                .orElseGet(UUID::randomUUID);

        ArtifactVersion artifact = createArtifactVersion(
                projectId,
                artifactId,
                artifactType,
                nextVersion,
                new SubmittedDocument(fileName, mediaType, content),
                Instant.now(clock)
        );
        projectRepository.saveArtifactVersion(projectId, artifact);
        return Optional.of(artifact);
    }

    public Optional<Project> findProject(UUID projectId) {
        return projectRepository.findProject(projectId);
    }

    @Transactional
    public Optional<Project> transitionProject(UUID projectId, WorkflowState nextState) {
        return findProject(projectId).map(project -> {
            Project transitioned = new Project(
                    project.projectId(),
                    project.name(),
                    project.businessOwner(),
                    project.domain(),
                    nextState,
                    project.createdAt()
            );
            projectRepository.updateProjectState(projectId, nextState);
            return transitioned;
        });
    }

    public Optional<ProjectContext> assembleContext(UUID projectId) {
        return findProject(projectId).map(project -> new ProjectContext(
                project.projectId(),
                project.name(),
                project.currentState(),
                List.copyOf(projectRepository.findArtifactVersions(projectId)),
                "mvp-default-policy",
                Instant.now(clock)
        ));
    }

    public Optional<ArtifactVersion> latestArtifact(UUID projectId, ArtifactType artifactType) {
        return projectRepository.latestArtifact(projectId, artifactType);
    }

    public byte[] retrieveArtifactContent(ArtifactVersion artifact) {
        return documentStoragePort.retrieve(
                new StoredDocument(
                        artifact.storageBucket(),
                        artifact.storageObjectKey(),
                        artifact.storageVersionId()
                )
        );
    }

    @Transactional
    public AgentExecution saveAgentExecution(
            UUID projectId,
            WorkflowState workflowState,
            String agentName,
            String agentVersion,
            String promptVersion,
            String model,
            AgentExecutionStatus status,
            double confidence,
            long inputTokens,
            long outputTokens,
            Instant startedAt,
            Instant completedAt,
            String summary
    ) {
        AgentExecution execution = new AgentExecution(
                UUID.randomUUID(),
                projectId,
                workflowState,
                agentName,
                agentVersion,
                promptVersion,
                model,
                status,
                confidence,
                inputTokens,
                outputTokens,
                startedAt,
                completedAt,
                summary
        );
        projectRepository.saveAgentExecution(execution);
        return execution;
    }

    private ArtifactVersion createArtifactVersion(UUID projectId, UUID artifactId, int version, SubmittedDocument document, Instant createdAt) {
        return createArtifactVersion(projectId, artifactId, ArtifactType.BRD, version, document, createdAt);
    }

    private ArtifactVersion createArtifactVersion(
            UUID projectId,
            UUID artifactId,
            ArtifactType artifactType,
            int version,
            SubmittedDocument document,
            Instant createdAt
    ) {
        String contentHash = sha256(document.content());
        String objectKey = "projects/%s/artifacts/%s/versions/%d/%s".formatted(
                projectId,
                artifactId,
                version,
                sanitizeFileName(document.fileName())
        );
        StoredDocument storedDocument = documentStoragePort.store(objectKey, document);

        return new ArtifactVersion(
                artifactId,
                artifactType,
                version,
                document.fileName(),
                document.mediaType(),
                document.sizeBytes(),
                contentHash,
                storedDocument.bucket(),
                storedDocument.objectKey(),
                storedDocument.storageVersionId(),
                createdAt
        );
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
