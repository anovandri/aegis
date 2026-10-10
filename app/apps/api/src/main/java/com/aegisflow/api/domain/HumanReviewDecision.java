package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HumanReviewDecision(
        UUID decisionId,
        UUID reviewId,
        UUID projectId,
        HumanDecision decision,
        String actor,
        String correction,
        List<String> clarificationQuestions,
        String rejectionReason,
        WorkflowState previousState,
        WorkflowState nextState,
        UUID generatedArtifactId,
        Integer generatedArtifactVersion,
        Instant createdAt
) {
    public HumanReviewDecision {
        clarificationQuestions = clarificationQuestions == null ? List.of() : List.copyOf(clarificationQuestions);
    }
}
