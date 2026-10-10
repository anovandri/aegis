package com.aegisflow.api.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HumanReview(
        UUID reviewId,
        UUID projectId,
        String projectName,
        ReviewType reviewType,
        ReviewStatus status,
        WorkflowState workflowState,
        String title,
        String summary,
        String assigneeRole,
        String assignee,
        String priority,
        Instant dueAt,
        String artifactSummary,
        List<String> blockingQuestions,
        Instant createdAt,
        Instant updatedAt
) {
    public HumanReview {
        blockingQuestions = blockingQuestions == null ? List.of() : List.copyOf(blockingQuestions);
    }
}
