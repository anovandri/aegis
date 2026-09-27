package com.aegisflow.api.workflow;

import com.aegisflow.api.domain.WorkflowState;

import java.util.List;
import java.util.UUID;

public record ProjectWorkflowProgress(
        UUID projectId,
        String workflowId,
        String runId,
        WorkflowState currentState,
        String status,
        List<String> completedSteps,
        List<String> pendingSteps
) {
}
