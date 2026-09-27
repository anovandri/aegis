package com.aegisflow.api.application;

import com.aegisflow.api.workflow.ProjectWorkflowProgress;

import java.util.UUID;

public interface ProjectLifecycleStarter {
    WorkflowStartResult start(UUID projectId);

    ProjectWorkflowProgress getProgress(UUID projectId);
}
