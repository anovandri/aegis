package com.aegisflow.api.application;

import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.workflow.ProjectWorkflowProgress;
import com.aegisflow.api.workflow.ReviewDecisionSignal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "aegisflow.temporal", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopProjectLifecycleStarter implements ProjectLifecycleStarter {
    private final ProjectService projectService;

    public NoopProjectLifecycleStarter(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public WorkflowStartResult start(UUID projectId) {
        return new WorkflowStartResult("temporal-disabled-%s".formatted(projectId), null);
    }

    @Override
    public ProjectWorkflowProgress getProgress(UUID projectId) {
        WorkflowState currentState = projectService.findProject(projectId)
                .map(project -> project.currentState())
                .orElse(null);
        return new ProjectWorkflowProgress(
                projectId,
                "temporal-disabled-%s".formatted(projectId),
                null,
                currentState,
                "TEMPORAL_DISABLED",
                List.of(),
                List.of(
                        "BRD_ANALYSIS",
                        "REQUIREMENT_ANALYSIS",
                        "ARCHITECTURE_ANALYSIS",
                        "SYSTEM_ANALYSIS",
                        "HUMAN_TECHNICAL_REVIEW",
                        "ESTIMATION",
                        "JIRA_DRAFT",
                        "HUMAN_PM_APPROVAL",
                        "JIRA_CREATED"
                )
        );
    }

    @Override
    public void signalReviewDecision(UUID projectId, ReviewDecisionSignal signal) {
        // Temporal is disabled in local/test mode; the persisted review decision remains authoritative.
    }
}
