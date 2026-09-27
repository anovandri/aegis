package com.aegisflow.api.workflow;

import com.aegisflow.api.domain.WorkflowState;

import java.util.UUID;

public record RequirementAnalysisActivityResult(
        UUID projectId,
        WorkflowState nextState,
        int analysisVersion,
        double readinessScore,
        String summary
) {
}
