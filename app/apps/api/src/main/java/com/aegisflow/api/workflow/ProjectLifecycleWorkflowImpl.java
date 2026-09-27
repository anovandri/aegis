package com.aegisflow.api.workflow;

import com.aegisflow.api.domain.WorkflowState;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.List;

public class ProjectLifecycleWorkflowImpl implements ProjectLifecycleWorkflow {
    private final ProjectLifecycleActivities brdIntakeActivities = Workflow.newActivityStub(
            ProjectLifecycleActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(3))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setMaximumInterval(Duration.ofSeconds(30))
                            .setBackoffCoefficient(2.0)
                            .setMaximumAttempts(3)
                            .setDoNotRetry(
                                    IllegalArgumentException.class.getName(),
                                    UnsupportedOperationException.class.getName()
                            )
                            .build())
                    .build()
    );
    private final ProjectLifecycleActivities requirementAnalysisActivities = Workflow.newActivityStub(
            ProjectLifecycleActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(5))
                            .setMaximumInterval(Duration.ofMinutes(1))
                            .setBackoffCoefficient(2.0)
                            .setMaximumAttempts(4)
                            .setDoNotRetry(
                                    IllegalArgumentException.class.getName(),
                                    UnsupportedOperationException.class.getName()
                            )
                            .build())
                    .build()
    );

    private ProjectWorkflowProgress progress;

    @Override
    public void start(ProjectLifecycleInput input) {
        String workflowId = Workflow.getInfo().getWorkflowId();
        String runId = Workflow.getInfo().getRunId();
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.BRD_ANALYSIS,
                "RUNNING",
                List.of("BRD_SUBMITTED"),
                pendingFrom("BRD_ANALYSIS")
        );

        BrdIntakeActivityResult brdIntakeResult = brdIntakeActivities.runBrdIntake(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                brdIntakeResult.nextState(),
                "RUNNING",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS"),
                pendingFrom("REQUIREMENT_ANALYSIS")
        );

        RequirementAnalysisActivityResult requirementAnalysisResult = requirementAnalysisActivities.runRequirementAnalysis(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                requirementAnalysisResult.nextState(),
                "WAITING_FOR_NEXT_ACTIVITY_IMPLEMENTATION",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS"),
                pendingFrom("ARCHITECTURE_ANALYSIS")
        );

        Workflow.await(() -> false);
    }

    @Override
    public ProjectWorkflowProgress getProgress() {
        return progress;
    }

    private List<String> pendingFrom(String firstPendingStep) {
        List<String> ordered = List.of(
                "BRD_ANALYSIS",
                "REQUIREMENT_ANALYSIS",
                "ARCHITECTURE_ANALYSIS",
                "SYSTEM_ANALYSIS",
                "HUMAN_TECHNICAL_REVIEW",
                "ESTIMATION",
                "JIRA_DRAFT",
                "HUMAN_PM_APPROVAL",
                "JIRA_CREATED"
        );
        int index = ordered.indexOf(firstPendingStep);
        return index < 0 ? ordered : ordered.subList(index, ordered.size());
    }
}
