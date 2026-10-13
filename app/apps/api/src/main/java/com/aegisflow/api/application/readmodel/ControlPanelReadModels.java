package com.aegisflow.api.application.readmodel;

import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ControlPanelReadModels {
    private ControlPanelReadModels() {
    }

    public record DashboardReadModel(
            long activeProjects,
            long pendingReviews,
            long pendingTechnicalReviews,
            long pendingPmApprovals,
            long jiraReadyProjects,
            long completedProjects,
            List<StateCount> projectsByState,
            List<ReviewQueueItem> urgentReviews,
            List<ProjectSummary> recentProjects,
            List<AgentRunSummary> recentAgentRuns
    ) {
    }

    public record StateCount(
            WorkflowState state,
            long count
    ) {
    }

    public record ProjectSummary(
            UUID projectId,
            String name,
            String businessOwner,
            String domain,
            WorkflowState currentState,
            Instant createdAt,
            int artifactCount,
            Instant latestArtifactAt,
            ReviewQueueItem pendingReview,
            AgentRunSummary latestAgentRun
    ) {
    }

    public record ProjectRoom(
            ProjectSummary project,
            List<ArtifactSummary> artifacts,
            List<ReviewQueueItem> reviews,
            List<AgentRunSummary> agentRuns,
            JiraDraftSummary jiraDraft,
            List<AuditEvent> activity
    ) {
    }

    public record ArtifactSummary(
            UUID artifactId,
            ArtifactType artifactType,
            int version,
            String fileName,
            String mediaType,
            long sizeBytes,
            String contentHash,
            Instant createdAt
    ) {
    }

    public record ReviewQueueItem(
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
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AgentRunSummary(
            UUID executionId,
            UUID projectId,
            String projectName,
            WorkflowState workflowState,
            String agentName,
            String agentVersion,
            String promptVersion,
            String model,
            AgentExecutionStatus status,
            double confidence,
            long inputTokens,
            long outputTokens,
            Instant startedAt,
            Instant completedAt,
            String summary
    ) {
    }

    public record AgentRunDetail(
            AgentRunSummary execution,
            List<ArtifactSummary> projectArtifacts,
            List<ReviewQueueItem> relatedReviews
    ) {
    }

    public record JiraDraftSummary(
            UUID projectId,
            String projectName,
            WorkflowState currentState,
            UUID artifactId,
            Integer version,
            String fileName,
            Instant createdAt,
            boolean readyForPmApproval,
            boolean published
    ) {
    }

    public record JiraDraftDetail(
            JiraDraftSummary draft,
            List<ReviewQueueItem> pmReviews,
            List<AuditEvent> activity
    ) {
    }

    public record AuditEvent(
            String eventId,
            String eventType,
            String actor,
            UUID projectId,
            String projectName,
            String subjectType,
            String subjectId,
            String message,
            Instant occurredAt
    ) {
    }

    public record OperationsHealth(
            long runningWorkflows,
            long waitingForTechnicalReview,
            long waitingForPmApproval,
            long completedProjects,
            long failedAgentRuns,
            long pendingReviews,
            Map<String, Long> agentRunsByStatus,
            List<WorkflowOperation> workflows
    ) {
    }

    public record WorkflowOperation(
            UUID projectId,
            String projectName,
            WorkflowState currentState,
            String operationalStatus,
            Instant createdAt,
            ReviewQueueItem pendingReview,
            AgentRunSummary latestAgentRun
    ) {
    }
}
