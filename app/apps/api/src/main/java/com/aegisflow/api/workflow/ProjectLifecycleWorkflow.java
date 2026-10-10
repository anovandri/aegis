package com.aegisflow.api.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ProjectLifecycleWorkflow {
    @WorkflowMethod
    void start(ProjectLifecycleInput input);

    @SignalMethod
    void recordReviewDecision(ReviewDecisionSignal signal);

    @QueryMethod
    ProjectWorkflowProgress getProgress();
}
