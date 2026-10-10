package com.aegisflow.api.workflow;

import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;

import java.util.UUID;

public record ReviewDecisionSignal(
        UUID projectId,
        UUID reviewId,
        ReviewType reviewType,
        HumanDecision decision,
        WorkflowState nextState,
        String actor
) {
}
