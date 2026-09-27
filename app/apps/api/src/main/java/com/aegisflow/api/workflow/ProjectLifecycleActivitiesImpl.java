package com.aegisflow.api.workflow;

import com.aegisflow.api.agent.BrdIntakeAgentPort;
import com.aegisflow.api.agent.BrdIntakeAnalysis;
import com.aegisflow.api.agent.BrdTextExtractor;
import com.aegisflow.api.agent.RequirementAnalysisAgentPort;
import com.aegisflow.api.agent.RequirementChecklist;
import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.ports.KnowledgeSearchPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class ProjectLifecycleActivitiesImpl implements ProjectLifecycleActivities {
    private final ProjectService projectService;
    private final BrdTextExtractor brdTextExtractor;
    private final BrdIntakeAgentPort brdIntakeAgent;
    private final RequirementAnalysisAgentPort requirementAnalysisAgent;
    private final RequirementChecklist requirementChecklist;
    private final KnowledgeSearchPort knowledgeSearchPort;
    private final ObjectMapper objectMapper;

    public ProjectLifecycleActivitiesImpl(
            ProjectService projectService,
            BrdTextExtractor brdTextExtractor,
            BrdIntakeAgentPort brdIntakeAgent,
            RequirementAnalysisAgentPort requirementAnalysisAgent,
            RequirementChecklist requirementChecklist,
            KnowledgeSearchPort knowledgeSearchPort,
            ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.brdTextExtractor = brdTextExtractor;
        this.brdIntakeAgent = brdIntakeAgent;
        this.requirementAnalysisAgent = requirementAnalysisAgent;
        this.requirementChecklist = requirementChecklist;
        this.knowledgeSearchPort = knowledgeSearchPort;
        this.objectMapper = objectMapper;
    }

    @Override
    public BrdIntakeActivityResult runBrdIntake(UUID projectId) {
        Instant startedAt = Instant.now();
        projectService.transitionProject(projectId, WorkflowState.BRD_ANALYSIS)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ArtifactVersion brdArtifact = projectService.latestArtifact(projectId, ArtifactType.BRD)
                .orElseThrow(() -> new IllegalStateException("Project has no BRD artifact: " + projectId));
        byte[] brdContent = projectService.retrieveArtifactContent(brdArtifact);
        String brdText = brdTextExtractor.extract(brdArtifact, brdContent);

        var analysis = brdIntakeAgent.analyze(projectId, brdArtifact.version(), brdText);
        byte[] analysisBytes = toJsonBytes(analysis);
        ArtifactVersion analysisArtifact = projectService.addGeneratedArtifact(
                        projectId,
                        ArtifactType.BRD_ANALYSIS,
                        "brd-analysis-v%d.json".formatted(brdArtifact.version()),
                        "application/json",
                        analysisBytes
                )
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        Instant completedAt = Instant.now();
        projectService.saveAgentExecution(
                projectId,
                WorkflowState.BRD_ANALYSIS,
                "BRD Intake Agent",
                "0.1.0",
                "brd-intake-v1",
                brdIntakeAgent.modelName(),
                AgentExecutionStatus.SUCCEEDED,
                analysis.confidence(),
                estimateTokens(brdText),
                estimateTokens(new String(analysisBytes, StandardCharsets.UTF_8)),
                startedAt,
                completedAt,
                analysis.summary()
        );

        projectService.transitionProject(projectId, WorkflowState.REQUIREMENT_ANALYSIS);
        return new BrdIntakeActivityResult(
                projectId,
                WorkflowState.REQUIREMENT_ANALYSIS,
                brdArtifact.version(),
                analysisArtifact.version(),
                analysis.completenessScore(),
                analysis.summary()
        );
    }

    private byte[] toJsonBytes(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("BRD Intake analysis could not be serialized", exception);
        }
    }

    private <T> T readJsonArtifact(ArtifactVersion artifact, Class<T> type) {
        try {
            return objectMapper.readValue(projectService.retrieveArtifactContent(artifact), type);
        } catch (Exception exception) {
            throw new IllegalStateException("Artifact %s v%d could not be parsed as %s"
                    .formatted(artifact.artifactType(), artifact.version(), type.getSimpleName()), exception);
        }
    }

    private long estimateTokens(String value) {
        return Math.max(1, value.length() / 4);
    }

    @Override
    public RequirementAnalysisActivityResult runRequirementAnalysis(UUID projectId) {
        Instant startedAt = Instant.now();
        projectService.transitionProject(projectId, WorkflowState.REQUIREMENT_ANALYSIS)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ArtifactVersion brdArtifact = projectService.latestArtifact(projectId, ArtifactType.BRD)
                .orElseThrow(() -> new IllegalStateException("Project has no BRD artifact: " + projectId));
        ArtifactVersion brdAnalysisArtifact = projectService.latestArtifact(projectId, ArtifactType.BRD_ANALYSIS)
                .orElseThrow(() -> new IllegalStateException("Project has no BRD analysis artifact: " + projectId));

        String brdText = brdTextExtractor.extract(brdArtifact, projectService.retrieveArtifactContent(brdArtifact));
        BrdIntakeAnalysis brdIntakeAnalysis = readJsonArtifact(brdAnalysisArtifact, BrdIntakeAnalysis.class);
        var knowledgeCitations = knowledgeSearchPort.retrieveRequirementAnalysisEvidence(projectId, brdText, brdIntakeAnalysis);

        var analysis = requirementAnalysisAgent.analyze(projectId, brdText, brdIntakeAnalysis, requirementChecklist, knowledgeCitations);
        byte[] analysisBytes = toJsonBytes(analysis);
        ArtifactVersion analysisArtifact = projectService.addGeneratedArtifact(
                        projectId,
                        ArtifactType.REQUIREMENT_ANALYSIS,
                        "requirement-analysis-v%d.json".formatted(brdAnalysisArtifact.version()),
                        "application/json",
                        analysisBytes
                )
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        Instant completedAt = Instant.now();
        projectService.saveAgentExecution(
                projectId,
                WorkflowState.REQUIREMENT_ANALYSIS,
                "Requirement Analyst Agent",
                "0.1.0",
                "requirement-analysis-v1",
                requirementAnalysisAgent.modelName(),
                AgentExecutionStatus.SUCCEEDED,
                analysis.confidence(),
                estimateTokens(brdText),
                estimateTokens(new String(analysisBytes, StandardCharsets.UTF_8)),
                startedAt,
                completedAt,
                analysis.summary()
        );

        projectService.transitionProject(projectId, WorkflowState.ARCHITECTURE_ANALYSIS);
        return new RequirementAnalysisActivityResult(
                projectId,
                WorkflowState.ARCHITECTURE_ANALYSIS,
                analysisArtifact.version(),
                analysis.readinessScore(),
                analysis.summary()
        );
    }

    @Override
    public void runArchitectureAnalysis(UUID projectId) {
        throw new UnsupportedOperationException("Architecture Analysis activity is not implemented yet");
    }

    @Override
    public void runSystemAnalysis(UUID projectId) {
        throw new UnsupportedOperationException("System Analysis activity is not implemented yet");
    }

    @Override
    public void createTechnicalReview(UUID projectId) {
        throw new UnsupportedOperationException("Technical Review activity is not implemented yet");
    }

    @Override
    public void runEstimation(UUID projectId) {
        throw new UnsupportedOperationException("Estimation activity is not implemented yet");
    }

    @Override
    public void runJiraPlanning(UUID projectId) {
        throw new UnsupportedOperationException("Jira Planning activity is not implemented yet");
    }

    @Override
    public void createPmReview(UUID projectId) {
        throw new UnsupportedOperationException("PM Review activity is not implemented yet");
    }

    @Override
    public void publishJira(UUID projectId) {
        throw new UnsupportedOperationException("Jira publish activity is not implemented yet");
    }
}
