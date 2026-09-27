package com.aegisflow.api.workflow;

import com.aegisflow.api.domain.WorkflowState;

import java.util.UUID;

public record BrdIntakeActivityResult(
        UUID projectId,
        WorkflowState nextState,
        int brdVersion,
        int analysisVersion,
        double completenessScore,
        String summary
) {
}
