package com.aegisflow.api.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ProjectLifecycleWorkflow {
    @WorkflowMethod
    void start(ProjectLifecycleInput input);

    @QueryMethod
    ProjectWorkflowProgress getProgress();
}
