package com.aegisflow.api.application;

public record WorkflowStartResult(
        String workflowId,
        String runId
) {
}
