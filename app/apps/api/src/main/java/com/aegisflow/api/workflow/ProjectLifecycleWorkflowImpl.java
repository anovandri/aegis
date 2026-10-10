package com.aegisflow.api.workflow;

import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.ReviewType;
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
    private ReviewDecisionSignal technicalReviewDecision;
    private ReviewDecisionSignal pmReviewDecision;

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
                "RUNNING",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS"),
                pendingFrom("ARCHITECTURE_ANALYSIS")
        );

        requirementAnalysisActivities.runArchitectureAnalysis(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.SYSTEM_ANALYSIS,
                "RUNNING",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS"),
                pendingFrom("SYSTEM_ANALYSIS")
        );

        requirementAnalysisActivities.runSystemAnalysis(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.HUMAN_TECHNICAL_REVIEW,
                "RUNNING",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS", "SYSTEM_ANALYSIS"),
                pendingFrom("HUMAN_TECHNICAL_REVIEW")
        );

        requirementAnalysisActivities.createTechnicalReview(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.HUMAN_TECHNICAL_REVIEW,
                "WAITING_FOR_TECHNICAL_REVIEW",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS", "SYSTEM_ANALYSIS"),
                pendingFrom("ESTIMATION")
        );

        Workflow.await(() -> technicalReviewDecision != null);
        if (!isApproved(technicalReviewDecision)) {
            progress = returnedProgress(
                    input.projectId(),
                    workflowId,
                    runId,
                    technicalReviewDecision.nextState(),
                    technicalReviewDecision.decision() == HumanDecision.REQUEST_CLARIFICATION
                            ? "WAITING_FOR_TECHNICAL_CLARIFICATION"
                            : "TECHNICAL_REVIEW_REJECTED",
                    "ARCHITECTURE_ANALYSIS"
            );
            Workflow.await(() -> false);
            return;
        }

        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.ESTIMATION,
                "RUNNING",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS", "SYSTEM_ANALYSIS", "HUMAN_TECHNICAL_REVIEW"),
                pendingFrom("ESTIMATION")
        );

        requirementAnalysisActivities.runEstimation(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.JIRA_DRAFT,
                "RUNNING",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS", "SYSTEM_ANALYSIS", "HUMAN_TECHNICAL_REVIEW", "ESTIMATION"),
                pendingFrom("JIRA_DRAFT")
        );

        requirementAnalysisActivities.runJiraPlanning(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.HUMAN_PM_APPROVAL,
                "RUNNING",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS", "SYSTEM_ANALYSIS", "HUMAN_TECHNICAL_REVIEW", "ESTIMATION", "JIRA_DRAFT"),
                pendingFrom("HUMAN_PM_APPROVAL")
        );

        requirementAnalysisActivities.createPmReview(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.HUMAN_PM_APPROVAL,
                "WAITING_FOR_PM_APPROVAL",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS", "SYSTEM_ANALYSIS", "HUMAN_TECHNICAL_REVIEW", "ESTIMATION", "JIRA_DRAFT"),
                pendingFrom("JIRA_CREATED")
        );

        Workflow.await(() -> pmReviewDecision != null);
        if (!isApproved(pmReviewDecision)) {
            progress = returnedProgress(
                    input.projectId(),
                    workflowId,
                    runId,
                    pmReviewDecision.nextState(),
                    "PM_APPROVAL_REJECTED",
                    "JIRA_DRAFT"
            );
            Workflow.await(() -> false);
            return;
        }

        requirementAnalysisActivities.publishJira(input.projectId());
        progress = new ProjectWorkflowProgress(
                input.projectId(),
                workflowId,
                runId,
                WorkflowState.JIRA_CREATED,
                "COMPLETED",
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS", "ARCHITECTURE_ANALYSIS", "SYSTEM_ANALYSIS", "HUMAN_TECHNICAL_REVIEW", "ESTIMATION", "JIRA_DRAFT", "HUMAN_PM_APPROVAL", "JIRA_CREATED"),
                List.of()
        );
    }

    @Override
    public void recordReviewDecision(ReviewDecisionSignal signal) {
        if (progress != null && !progress.projectId().equals(signal.projectId())) {
            return;
        }
        if (signal.reviewType() == ReviewType.TECHNICAL_REVIEW) {
            technicalReviewDecision = signal;
        }
        if (signal.reviewType() == ReviewType.PM_APPROVAL) {
            pmReviewDecision = signal;
        }
    }

    @Override
    public ProjectWorkflowProgress getProgress() {
        return progress;
    }

    private boolean isApproved(ReviewDecisionSignal signal) {
        return signal.decision() == HumanDecision.APPROVE
                || signal.decision() == HumanDecision.APPROVE_WITH_CHANGE;
    }

    private ProjectWorkflowProgress returnedProgress(
            java.util.UUID projectId,
            String workflowId,
            String runId,
            WorkflowState currentState,
            String status,
            String pendingFrom
    ) {
        return new ProjectWorkflowProgress(
                projectId,
                workflowId,
                runId,
                currentState,
                status,
                List.of("BRD_SUBMITTED", "BRD_ANALYSIS", "REQUIREMENT_ANALYSIS"),
                pendingFrom(pendingFrom)
        );
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
