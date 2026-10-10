package com.aegisflow.api.workflow;

import com.aegisflow.api.agent.BrdIntakeAgentPort;
import com.aegisflow.api.agent.BrdIntakeAnalysis;
import com.aegisflow.api.agent.BrdTextExtractor;
import com.aegisflow.api.agent.RequirementAnalysisAgentPort;
import com.aegisflow.api.agent.RequirementChecklist;
import com.aegisflow.api.application.ProjectService;
import com.aegisflow.api.application.ReviewService;
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
import java.util.Map;
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
    private final ReviewService reviewService;

    public ProjectLifecycleActivitiesImpl(
            ProjectService projectService,
            BrdTextExtractor brdTextExtractor,
            BrdIntakeAgentPort brdIntakeAgent,
            RequirementAnalysisAgentPort requirementAnalysisAgent,
            RequirementChecklist requirementChecklist,
            KnowledgeSearchPort knowledgeSearchPort,
            ObjectMapper objectMapper,
            ReviewService reviewService
    ) {
        this.projectService = projectService;
        this.brdTextExtractor = brdTextExtractor;
        this.brdIntakeAgent = brdIntakeAgent;
        this.requirementAnalysisAgent = requirementAnalysisAgent;
        this.requirementChecklist = requirementChecklist;
        this.knowledgeSearchPort = knowledgeSearchPort;
        this.objectMapper = objectMapper;
        this.reviewService = reviewService;
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
        Instant startedAt = Instant.now();
        projectService.transitionProject(projectId, WorkflowState.ARCHITECTURE_ANALYSIS)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ArtifactVersion requirementAnalysis = projectService.latestArtifact(projectId, ArtifactType.REQUIREMENT_ANALYSIS)
                .orElseThrow(() -> new IllegalStateException("Project has no requirement analysis artifact: " + projectId));
        byte[] artifactBytes = toJsonBytes(Map.of(
                "status", "DRAFT",
                "summary", "MVP architecture analysis generated from requirement analysis artifact v%d.".formatted(requirementAnalysis.version()),
                "reuseInvestigation", Map.of(
                        "existingService", "To be confirmed by Digital Architecture",
                        "existingApi", "To be confirmed by API catalog integration",
                        "existingDatabase", "To be confirmed by database catalog integration",
                        "existingEvent", "To be confirmed by event catalog integration",
                        "existingPattern", "Prefer reuse before introducing new services"
                ),
                "recommendations", java.util.List.of(
                        "Validate reusable enterprise capabilities before creating new components.",
                        "Confirm synchronous versus asynchronous integration in technical review.",
                        "Record architecture risks as review blocking questions."
                )
        ));

        ArtifactVersion architectureArtifact = projectService.addGeneratedArtifact(
                        projectId,
                        ArtifactType.ARCHITECTURE_ANALYSIS,
                        "architecture-analysis-v%d.json".formatted(requirementAnalysis.version()),
                        "application/json",
                        artifactBytes
                )
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        saveDeterministicExecution(
                projectId,
                WorkflowState.ARCHITECTURE_ANALYSIS,
                "Architecture Analysis Activity",
                startedAt,
                artifactBytes,
                "Architecture analysis artifact v%d generated.".formatted(architectureArtifact.version())
        );
        projectService.transitionProject(projectId, WorkflowState.SYSTEM_ANALYSIS);
    }

    @Override
    public void runSystemAnalysis(UUID projectId) {
        Instant startedAt = Instant.now();
        projectService.transitionProject(projectId, WorkflowState.SYSTEM_ANALYSIS)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ArtifactVersion architectureAnalysis = projectService.latestArtifact(projectId, ArtifactType.ARCHITECTURE_ANALYSIS)
                .orElseThrow(() -> new IllegalStateException("Project has no architecture analysis artifact: " + projectId));
        byte[] artifactBytes = toJsonBytes(Map.of(
                "status", "DRAFT",
                "summary", "MVP system analysis generated from architecture analysis artifact v%d.".formatted(architectureAnalysis.version()),
                "apiInteractions", java.util.List.of("Define request/response contracts during detailed system analysis."),
                "sequenceFlows", java.util.List.of("Capture happy path, retry path, timeout path, and reversal path."),
                "errorHandling", java.util.List.of("Define deterministic error codes and audit events."),
                "dependencies", java.util.List.of("Confirm impacted systems during technical review.")
        ));

        ArtifactVersion systemArtifact = projectService.addGeneratedArtifact(
                        projectId,
                        ArtifactType.SYSTEM_ANALYSIS,
                        "system-analysis-v%d.json".formatted(architectureAnalysis.version()),
                        "application/json",
                        artifactBytes
                )
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        saveDeterministicExecution(
                projectId,
                WorkflowState.SYSTEM_ANALYSIS,
                "System Analysis Activity",
                startedAt,
                artifactBytes,
                "System analysis artifact v%d generated.".formatted(systemArtifact.version())
        );
    }

    @Override
    public void createTechnicalReview(UUID projectId) {
        reviewService.createTechnicalReview(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    @Override
    public void runEstimation(UUID projectId) {
        Instant startedAt = Instant.now();
        projectService.transitionProject(projectId, WorkflowState.ESTIMATION)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ArtifactVersion systemAnalysis = projectService.latestArtifact(projectId, ArtifactType.SYSTEM_ANALYSIS)
                .orElseThrow(() -> new IllegalStateException("Project has no system analysis artifact: " + projectId));
        byte[] artifactBytes = toJsonBytes(Map.of(
                "status", "DRAFT",
                "summary", "MVP estimation generated from system analysis artifact v%d.".formatted(systemAnalysis.version()),
                "complexity", "MEDIUM",
                "disciplines", java.util.List.of("Backend", "QA", "Architecture Review", "Project Management"),
                "estimate", Map.of(
                        "backendDays", 5,
                        "qaDays", 3,
                        "architectureReviewDays", 1,
                        "projectManagementDays", 1
                ),
                "unknowns", java.util.List.of(
                        "Reuse capability confirmation",
                        "External dependency availability",
                        "Non-functional requirement completeness"
                )
        ));

        ArtifactVersion estimationArtifact = projectService.addGeneratedArtifact(
                        projectId,
                        ArtifactType.ESTIMATION,
                        "estimation-v%d.json".formatted(systemAnalysis.version()),
                        "application/json",
                        artifactBytes
                )
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        saveDeterministicExecution(
                projectId,
                WorkflowState.ESTIMATION,
                "Estimation Activity",
                startedAt,
                artifactBytes,
                "Estimation artifact v%d generated.".formatted(estimationArtifact.version())
        );
        projectService.transitionProject(projectId, WorkflowState.JIRA_DRAFT);
    }

    @Override
    public void runJiraPlanning(UUID projectId) {
        Instant startedAt = Instant.now();
        projectService.transitionProject(projectId, WorkflowState.JIRA_DRAFT)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ArtifactVersion estimation = projectService.latestArtifact(projectId, ArtifactType.ESTIMATION)
                .orElseThrow(() -> new IllegalStateException("Project has no estimation artifact: " + projectId));
        byte[] artifactBytes = toJsonBytes(Map.of(
                "status", "DRAFT",
                "summary", "MVP Jira draft generated from estimation artifact v%d.".formatted(estimation.version()),
                "epic", Map.of(
                        "summary", "Implement approved business requirement",
                        "description", "Generated draft pending PM approval"
                ),
                "stories", java.util.List.of(
                        Map.of(
                                "summary", "Backend implementation",
                                "tasks", java.util.List.of("API contract", "Business logic", "Persistence", "Observability")
                        ),
                        Map.of(
                                "summary", "Quality assurance",
                                "tasks", java.util.List.of("Integration tests", "E2E scenarios", "Regression checks")
                        )
                )
        ));

        ArtifactVersion jiraPlanArtifact = projectService.addGeneratedArtifact(
                        projectId,
                        ArtifactType.JIRA_PLAN,
                        "jira-plan-v%d.json".formatted(estimation.version()),
                        "application/json",
                        artifactBytes
                )
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        saveDeterministicExecution(
                projectId,
                WorkflowState.JIRA_DRAFT,
                "Jira Planning Activity",
                startedAt,
                artifactBytes,
                "Jira plan artifact v%d generated.".formatted(jiraPlanArtifact.version())
        );
    }

    @Override
    public void createPmReview(UUID projectId) {
        reviewService.createPmReview(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    @Override
    public void publishJira(UUID projectId) {
        Instant startedAt = Instant.now();
        projectService.latestArtifact(projectId, ArtifactType.JIRA_PLAN)
                .orElseThrow(() -> new IllegalStateException("Project has no Jira plan artifact: " + projectId));
        projectService.transitionProject(projectId, WorkflowState.JIRA_CREATED)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        saveDeterministicExecution(
                projectId,
                WorkflowState.JIRA_CREATED,
                "Jira Publish Activity",
                startedAt,
                new byte[0],
                "Jira publish placeholder completed; external Jira creation is still disabled for MVP."
        );
    }

    private void saveDeterministicExecution(
            UUID projectId,
            WorkflowState workflowState,
            String activityName,
            Instant startedAt,
            byte[] outputBytes,
            String summary
    ) {
        projectService.saveAgentExecution(
                projectId,
                workflowState,
                activityName,
                "0.1.0",
                "deterministic-mvp-v1",
                "deterministic-service",
                AgentExecutionStatus.SUCCEEDED,
                1.0,
                1,
                estimateTokens(new String(outputBytes, StandardCharsets.UTF_8)),
                startedAt,
                Instant.now(),
                summary
        );
    }
}
