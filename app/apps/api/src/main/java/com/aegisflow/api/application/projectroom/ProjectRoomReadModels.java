package com.aegisflow.api.application.projectroom;

import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectRoomReadModels {
    private ProjectRoomReadModels() {
    }

    public record ProjectRoom(
            ProjectRoomProject project,
            List<ProjectRoomArtifact> artifacts,
            List<ProjectRoomReview> reviews,
            List<ProjectRoomAgentRun> agentRuns,
            ProjectRoomJiraDraft jiraDraft,
            List<ProjectMessage> messages,
            List<ProjectRoomActivityEvent> activity
    ) {
    }

    public record ProjectRoomProject(
            UUID projectId,
            String name,
            String businessOwner,
            String domain,
            WorkflowState currentState,
            Instant createdAt,
            int artifactCount,
            int staleArtifactCount,
            boolean hasStaleArtifacts,
            List<String> riskFlags,
            Instant latestArtifactAt,
            ProjectRoomReview pendingReview,
            ProjectRoomAgentRun latestAgentRun
    ) {
    }

    public record ProjectRoomArtifact(
            UUID artifactId,
            ArtifactType artifactType,
            int version,
            String fileName,
            String mediaType,
            long sizeBytes,
            String contentHash,
            Instant createdAt,
            boolean stale,
            List<String> staleReasons
    ) {
    }

    public record ProjectRoomReview(
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

    public record ProjectRoomAgentRun(
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

    public record ProjectRoomJiraDraft(
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

    public record ProjectMessage(
            UUID messageId,
            UUID projectId,
            String actor,
            String body,
            String anchorType,
            String anchorId,
            List<String> mentions,
            Instant createdAt
    ) {
    }

    public record ProjectRoomActivityEvent(
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
}
