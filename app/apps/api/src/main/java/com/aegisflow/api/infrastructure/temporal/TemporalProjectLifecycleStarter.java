package com.aegisflow.api.infrastructure.temporal;

import com.aegisflow.api.application.ProjectLifecycleStarter;
import com.aegisflow.api.application.WorkflowStartResult;
import com.aegisflow.api.workflow.ProjectLifecycleInput;
import com.aegisflow.api.workflow.ProjectLifecycleWorkflow;
import com.aegisflow.api.workflow.ProjectWorkflowProgress;
import com.aegisflow.api.workflow.ReviewDecisionSignal;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.api.common.v1.WorkflowExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "aegisflow.temporal", name = "enabled", havingValue = "true")
public class TemporalProjectLifecycleStarter implements ProjectLifecycleStarter {
    private final WorkflowClient workflowClient;
    private final TemporalProperties properties;

    public TemporalProjectLifecycleStarter(WorkflowClient workflowClient, TemporalProperties properties) {
        this.workflowClient = workflowClient;
        this.properties = properties;
    }

    @Override
    public WorkflowStartResult start(UUID projectId) {
        ProjectLifecycleWorkflow workflow = workflowClient.newWorkflowStub(
                ProjectLifecycleWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId(projectId))
                        .setTaskQueue(properties.taskQueue())
                        .build()
        );
        WorkflowExecution execution = WorkflowClient.start(workflow::start, new ProjectLifecycleInput(projectId));
        return new WorkflowStartResult(execution.getWorkflowId(), execution.getRunId());
    }

    @Override
    public ProjectWorkflowProgress getProgress(UUID projectId) {
        WorkflowStub workflowStub = workflowClient.newUntypedWorkflowStub(workflowId(projectId));
        return workflowStub.query("getProgress", ProjectWorkflowProgress.class);
    }

    @Override
    public void signalReviewDecision(UUID projectId, ReviewDecisionSignal signal) {
        ProjectLifecycleWorkflow workflow = workflowClient.newWorkflowStub(
                ProjectLifecycleWorkflow.class,
                workflowId(projectId)
        );
        workflow.recordReviewDecision(signal);
    }

    private String workflowId(UUID projectId) {
        return "project-lifecycle-%s".formatted(projectId);
    }
}
