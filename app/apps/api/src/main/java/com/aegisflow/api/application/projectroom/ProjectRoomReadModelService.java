package com.aegisflow.api.application.projectroom;

import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectMessage;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoom;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomActivityEvent;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomAgentRun;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomArtifact;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomArtifactContent;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomArtifactDetail;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomJiraDraft;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomProject;
import com.aegisflow.api.application.projectroom.ProjectRoomReadModels.ProjectRoomReview;
import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;
import com.aegisflow.api.ports.DocumentStoragePort;
import com.aegisflow.api.ports.StoredDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProjectRoomReadModelService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*)");

    private final JdbcTemplate jdbcTemplate;
    private final DocumentStoragePort documentStoragePort;
    private final Clock clock;

    public ProjectRoomReadModelService(JdbcTemplate jdbcTemplate, DocumentStoragePort documentStoragePort, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentStoragePort = documentStoragePort;
        this.clock = clock;
    }

    public Optional<ProjectRoom> projectRoom(UUID projectId) {
        return project(projectId).map(project -> new ProjectRoom(
                project,
                artifacts(projectId),
                reviews(projectId),
                agentRuns(projectId, 20),
                jiraDraft(projectId).orElse(null),
                projectMessages(projectId),
                activity(projectId, 30)
        ));
    }

    public Optional<List<ProjectRoomArtifact>> artifactsIfProjectExists(UUID projectId) {
        if (!projectExists(projectId)) {
            return Optional.empty();
        }
        return Optional.of(artifacts(projectId));
    }

    public Optional<ProjectRoomArtifactDetail> artifactDetail(UUID projectId, UUID artifactId, int version) {
        return storedArtifact(projectId, artifactId, version)
                .map(artifact -> new ProjectRoomArtifactDetail(
                        artifact.artifact(),
                        true,
                        "/api/project-rooms/%s/artifacts/%s/versions/%d/content".formatted(projectId, artifactId, version)
                ));
    }

    public Optional<ProjectRoomArtifactContent> artifactContent(UUID projectId, UUID artifactId, int version) {
        return storedArtifact(projectId, artifactId, version)
                .map(artifact -> new ProjectRoomArtifactContent(
                        artifact.artifact(),
                        artifact.artifact().fileName(),
                        artifact.artifact().mediaType(),
                        documentStoragePort.retrieve(new StoredDocument(
                                artifact.storageBucket(),
                                artifact.storageObjectKey(),
                                artifact.storageVersionId()
                        ))
                ));
    }

    public Optional<List<ProjectRoomActivityEvent>> activityIfProjectExists(UUID projectId, int limit) {
        if (!projectExists(projectId)) {
            return Optional.empty();
        }
        return Optional.of(activity(projectId, limit));
    }

    public Optional<List<ProjectMessage>> messagesIfProjectExists(UUID projectId) {
        if (!projectExists(projectId)) {
            return Optional.empty();
        }
        return Optional.of(projectMessages(projectId));
    }

    public List<ProjectMessage> projectMessages(UUID projectId) {
        return jdbcTemplate.query(
                """
                        SELECT *
                        FROM project_messages
                        WHERE project_id = ?
                        ORDER BY created_at ASC
                        """,
                this::projectMessage,
                projectId
        );
    }

    @Transactional
    public Optional<ProjectMessage> addProjectMessage(UUID projectId, String actor, String body, String anchorType, String anchorId) {
        if (!projectExists(projectId)) {
            return Optional.empty();
        }
        UUID messageId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        List<String> mentions = extractMentions(body);
        String storedAnchorType = blankToNull(anchorType);
        String storedAnchorId = blankToNull(anchorId);
        String storedActor = requireText(actor, "Actor is required");
        String storedBody = requireText(body, "Message body is required");

        jdbcTemplate.update(
                """
                        INSERT INTO project_messages (
                            message_id,
                            project_id,
                            actor,
                            body,
                            anchor_type,
                            anchor_id,
                            mentions,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                messageId,
                projectId,
                storedActor,
                storedBody,
                storedAnchorType,
                storedAnchorId,
                String.join("\n---\n", mentions),
                Timestamp.from(now)
        );

        return Optional.of(new ProjectMessage(
                messageId,
                projectId,
                storedActor,
                storedBody,
                storedAnchorType,
                storedAnchorId,
                mentions,
                now
        ));
    }

    public List<String> suggestedProjectRoomActions(UUID projectId) {
        return project(projectId)
                .map(project -> {
                    java.util.ArrayList<String> actions = new java.util.ArrayList<>();
                    if (project.pendingReview() != null) {
                        actions.add("OPEN_PENDING_REVIEW");
                    }
                    if (project.hasStaleArtifacts()) {
                        actions.add("UPLOAD_REVISED_BRD_OR_RERUN_ANALYSIS");
                    }
                    if (hasFailedAgentRun(projectId)) {
                        actions.add("INSPECT_FAILED_AGENT_RUN");
                    }
                    if (actions.isEmpty()) {
                        actions.add("MONITOR_PROJECT_ACTIVITY");
                    }
                    return List.copyOf(actions);
                })
                .orElse(List.of());
    }

    private Optional<ProjectRoomProject> project(UUID projectId) {
        return jdbcTemplate.query(
                        """
                                SELECT
                                    p.project_id,
                                    p.name,
                                    p.business_owner,
                                    p.domain,
                                    p.current_state,
                                    p.created_at,
                                    COUNT(av.artifact_id) AS artifact_count,
                                    MAX(av.created_at) AS latest_artifact_at
                                FROM projects p
                                LEFT JOIN artifact_versions av ON av.project_id = p.project_id
                                WHERE p.project_id = ?
                                GROUP BY p.project_id, p.name, p.business_owner, p.domain, p.current_state, p.created_at
                                """,
                        this::projectRow,
                        projectId
                )
                .stream()
                .findFirst();
    }

    private ProjectRoomProject projectRow(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID projectId = uuid(resultSet, "project_id");
        List<ProjectRoomArtifact> projectArtifacts = artifacts(projectId);
        int staleArtifactCount = (int) projectArtifacts.stream().filter(ProjectRoomArtifact::stale).count();
        return new ProjectRoomProject(
                projectId,
                resultSet.getString("name"),
                resultSet.getString("business_owner"),
                resultSet.getString("domain"),
                WorkflowState.valueOf(resultSet.getString("current_state")),
                instant(resultSet, "created_at"),
                resultSet.getInt("artifact_count"),
                staleArtifactCount,
                staleArtifactCount > 0,
                riskFlags(projectId, staleArtifactCount),
                nullableInstant(resultSet, "latest_artifact_at"),
                pendingReview(projectId).orElse(null),
                latestAgentRun(projectId).orElse(null)
        );
    }

    private boolean projectExists(UUID projectId) {
        return count("SELECT COUNT(*) FROM projects WHERE project_id = ?", projectId) > 0;
    }

    private List<ProjectRoomArtifact> artifacts(UUID projectId) {
        return jdbcTemplate.query(
                """
                        SELECT
                            artifact_id,
                            artifact_type,
                            version,
                            file_name,
                            media_type,
                            size_bytes,
                            content_hash,
                            created_at,
                            MAX(CASE WHEN artifact_type = 'BRD' THEN created_at ELSE NULL END) OVER (PARTITION BY project_id) AS latest_brd_created_at
                        FROM artifact_versions
                        WHERE project_id = ?
                        ORDER BY artifact_type, version DESC
                        """,
                this::artifact,
                projectId
        );
    }

    private Optional<StoredProjectRoomArtifact> storedArtifact(UUID projectId, UUID artifactId, int version) {
        return jdbcTemplate.query(
                        """
                                SELECT
                                    artifact_id,
                                    artifact_type,
                                    version,
                                    file_name,
                                    media_type,
                                    size_bytes,
                                    content_hash,
                                    storage_bucket,
                                    storage_object_key,
                                    storage_version_id,
                                    created_at,
                                    MAX(CASE WHEN artifact_type = 'BRD' THEN created_at ELSE NULL END) OVER (PARTITION BY project_id) AS latest_brd_created_at
                                FROM artifact_versions
                                WHERE project_id = ?
                                  AND artifact_id = ?
                                  AND version = ?
                                """,
                        this::storedArtifact,
                        projectId,
                        artifactId,
                        version
                )
                .stream()
                .findFirst();
    }

    private StoredProjectRoomArtifact storedArtifact(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StoredProjectRoomArtifact(
                artifact(resultSet, rowNumber),
                resultSet.getString("storage_bucket"),
                resultSet.getString("storage_object_key"),
                resultSet.getString("storage_version_id")
        );
    }

    private ProjectRoomArtifact artifact(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectRoomArtifact(
                uuid(resultSet, "artifact_id"),
                ArtifactType.valueOf(resultSet.getString("artifact_type")),
                resultSet.getInt("version"),
                resultSet.getString("file_name"),
                resultSet.getString("media_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("content_hash"),
                instant(resultSet, "created_at"),
                isStaleArtifact(resultSet),
                staleReasons(resultSet)
        );
    }

    private boolean isStaleArtifact(ResultSet resultSet) throws SQLException {
        ArtifactType artifactType = ArtifactType.valueOf(resultSet.getString("artifact_type"));
        Instant latestBrdCreatedAt = nullableInstant(resultSet, "latest_brd_created_at");
        return artifactType != ArtifactType.BRD
                && latestBrdCreatedAt != null
                && instant(resultSet, "created_at").isBefore(latestBrdCreatedAt);
    }

    private List<String> staleReasons(ResultSet resultSet) throws SQLException {
        if (!isStaleArtifact(resultSet)) {
            return List.of();
        }
        return List.of("A newer BRD version exists after this artifact was generated");
    }

    private Optional<ProjectRoomReview> pendingReview(UUID projectId) {
        return jdbcTemplate.query(
                        """
                                SELECT *
                                FROM human_reviews
                                WHERE project_id = ?
                                  AND status <> 'DECIDED'
                                ORDER BY due_at NULLS LAST, created_at DESC
                                LIMIT 1
                                """,
                        this::review,
                        projectId
                )
                .stream()
                .findFirst();
    }

    private List<ProjectRoomReview> reviews(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT * FROM human_reviews WHERE project_id = ? ORDER BY created_at DESC",
                this::review,
                projectId
        );
    }

    private ProjectRoomReview review(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectRoomReview(
                uuid(resultSet, "review_id"),
                uuid(resultSet, "project_id"),
                resultSet.getString("project_name"),
                ReviewType.valueOf(resultSet.getString("review_type")),
                ReviewStatus.valueOf(resultSet.getString("status")),
                WorkflowState.valueOf(resultSet.getString("workflow_state")),
                resultSet.getString("title"),
                resultSet.getString("summary"),
                resultSet.getString("assignee_role"),
                resultSet.getString("assignee"),
                resultSet.getString("priority"),
                nullableInstant(resultSet, "due_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at")
        );
    }

    private List<ProjectRoomAgentRun> agentRuns(UUID projectId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT ae.*, p.name AS project_name
                        FROM agent_executions ae
                        JOIN projects p ON p.project_id = ae.project_id
                        WHERE ae.project_id = ?
                        ORDER BY ae.started_at DESC
                        LIMIT ?
                        """,
                this::agentRun,
                projectId,
                limit
        );
    }

    private Optional<ProjectRoomAgentRun> latestAgentRun(UUID projectId) {
        return agentRuns(projectId, 1).stream().findFirst();
    }

    private ProjectRoomAgentRun agentRun(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectRoomAgentRun(
                uuid(resultSet, "execution_id"),
                uuid(resultSet, "project_id"),
                resultSet.getString("project_name"),
                WorkflowState.valueOf(resultSet.getString("workflow_state")),
                resultSet.getString("agent_name"),
                resultSet.getString("agent_version"),
                resultSet.getString("prompt_version"),
                resultSet.getString("model"),
                AgentExecutionStatus.valueOf(resultSet.getString("status")),
                resultSet.getDouble("confidence"),
                resultSet.getLong("input_tokens"),
                resultSet.getLong("output_tokens"),
                instant(resultSet, "started_at"),
                instant(resultSet, "completed_at"),
                resultSet.getString("summary")
        );
    }

    private Optional<ProjectRoomJiraDraft> jiraDraft(UUID projectId) {
        return jdbcTemplate.query(
                        """
                                SELECT
                                    p.project_id,
                                    p.name AS project_name,
                                    p.current_state,
                                    av.artifact_id,
                                    av.version,
                                    av.file_name,
                                    av.created_at
                                FROM projects p
                                JOIN artifact_versions av ON av.project_id = p.project_id
                                WHERE p.project_id = ?
                                  AND av.artifact_type = 'JIRA_PLAN'
                                ORDER BY av.version DESC
                                LIMIT 1
                                """,
                        this::jiraDraftRow,
                        projectId
                )
                .stream()
                .findFirst();
    }

    private ProjectRoomJiraDraft jiraDraftRow(ResultSet resultSet, int rowNumber) throws SQLException {
        WorkflowState currentState = WorkflowState.valueOf(resultSet.getString("current_state"));
        return new ProjectRoomJiraDraft(
                uuid(resultSet, "project_id"),
                resultSet.getString("project_name"),
                currentState,
                uuid(resultSet, "artifact_id"),
                resultSet.getInt("version"),
                resultSet.getString("file_name"),
                instant(resultSet, "created_at"),
                currentState == WorkflowState.HUMAN_PM_APPROVAL,
                currentState == WorkflowState.JIRA_CREATED
        );
    }

    private List<ProjectRoomActivityEvent> activity(UUID projectId, int limit) {
        java.util.ArrayList<ProjectRoomActivityEvent> events = new java.util.ArrayList<>();
        events.addAll(projectCreatedEvents(projectId, limit));
        events.addAll(artifactEvents(projectId, limit));
        events.addAll(agentExecutionEvents(projectId, limit));
        events.addAll(reviewEvents(projectId, limit));
        events.addAll(projectMessageEvents(projectId, limit));
        return events.stream()
                .sorted(Comparator.comparing(ProjectRoomActivityEvent::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    private List<ProjectRoomActivityEvent> projectCreatedEvents(UUID projectId, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM projects WHERE project_id = ? ORDER BY created_at DESC LIMIT ?",
                (resultSet, rowNumber) -> new ProjectRoomActivityEvent(
                        "project-" + uuid(resultSet, "project_id"),
                        "PROJECT_CREATED",
                        "system",
                        uuid(resultSet, "project_id"),
                        resultSet.getString("name"),
                        "PROJECT",
                        uuid(resultSet, "project_id").toString(),
                        "Project created in state " + resultSet.getString("current_state"),
                        instant(resultSet, "created_at")
                ),
                projectId,
                limit
        );
    }

    private List<ProjectRoomActivityEvent> artifactEvents(UUID projectId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT av.*, p.name AS project_name
                        FROM artifact_versions av
                        JOIN projects p ON p.project_id = av.project_id
                        WHERE av.project_id = ?
                        ORDER BY av.created_at DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new ProjectRoomActivityEvent(
                        "artifact-" + uuid(resultSet, "artifact_id") + "-v" + resultSet.getInt("version"),
                        "ARTIFACT_VERSION_CREATED",
                        "system",
                        uuid(resultSet, "project_id"),
                        resultSet.getString("project_name"),
                        "ARTIFACT",
                        uuid(resultSet, "artifact_id") + ":" + resultSet.getInt("version"),
                        resultSet.getString("artifact_type") + " v" + resultSet.getInt("version") + " stored as " + resultSet.getString("file_name"),
                        instant(resultSet, "created_at")
                ),
                projectId,
                limit
        );
    }

    private List<ProjectRoomActivityEvent> agentExecutionEvents(UUID projectId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT ae.*, p.name AS project_name
                        FROM agent_executions ae
                        JOIN projects p ON p.project_id = ae.project_id
                        WHERE ae.project_id = ?
                        ORDER BY ae.completed_at DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new ProjectRoomActivityEvent(
                        "agent-" + uuid(resultSet, "execution_id"),
                        "AGENT_EXECUTION_" + resultSet.getString("status"),
                        resultSet.getString("agent_name"),
                        uuid(resultSet, "project_id"),
                        resultSet.getString("project_name"),
                        "AGENT_EXECUTION",
                        uuid(resultSet, "execution_id").toString(),
                        resultSet.getString("summary"),
                        instant(resultSet, "completed_at")
                ),
                projectId,
                limit
        );
    }

    private List<ProjectRoomActivityEvent> reviewEvents(UUID projectId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT hr.*, p.name AS project_name
                        FROM human_reviews hr
                        JOIN projects p ON p.project_id = hr.project_id
                        WHERE hr.project_id = ?
                        ORDER BY hr.updated_at DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new ProjectRoomActivityEvent(
                        "review-" + uuid(resultSet, "review_id") + "-" + resultSet.getString("status"),
                        "HUMAN_REVIEW_" + resultSet.getString("status"),
                        resultSet.getString("assignee") == null ? resultSet.getString("assignee_role") : resultSet.getString("assignee"),
                        uuid(resultSet, "project_id"),
                        resultSet.getString("project_name"),
                        "HUMAN_REVIEW",
                        uuid(resultSet, "review_id").toString(),
                        resultSet.getString("title"),
                        instant(resultSet, "updated_at")
                ),
                projectId,
                limit
        );
    }

    private List<ProjectRoomActivityEvent> projectMessageEvents(UUID projectId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT pm.*, p.name AS project_name
                        FROM project_messages pm
                        JOIN projects p ON p.project_id = pm.project_id
                        WHERE pm.project_id = ?
                        ORDER BY pm.created_at DESC
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new ProjectRoomActivityEvent(
                        "message-" + uuid(resultSet, "message_id"),
                        "PROJECT_MESSAGE_ADDED",
                        resultSet.getString("actor"),
                        uuid(resultSet, "project_id"),
                        resultSet.getString("project_name"),
                        "PROJECT_MESSAGE",
                        uuid(resultSet, "message_id").toString(),
                        resultSet.getString("body"),
                        instant(resultSet, "created_at")
                ),
                projectId,
                limit
        );
    }

    private ProjectMessage projectMessage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProjectMessage(
                uuid(resultSet, "message_id"),
                uuid(resultSet, "project_id"),
                resultSet.getString("actor"),
                resultSet.getString("body"),
                resultSet.getString("anchor_type"),
                resultSet.getString("anchor_id"),
                split(resultSet.getString("mentions")),
                instant(resultSet, "created_at")
        );
    }

    private List<String> riskFlags(UUID projectId, int staleArtifactCount) {
        java.util.ArrayList<String> flags = new java.util.ArrayList<>();
        if (staleArtifactCount > 0) {
            flags.add("STALE_ARTIFACTS");
        }
        if (count(
                """
                        SELECT COUNT(*)
                        FROM human_reviews
                        WHERE project_id = ?
                          AND status <> 'DECIDED'
                          AND LOWER(priority) = 'high'
                        """,
                projectId
        ) > 0) {
            flags.add("HIGH_PRIORITY_REVIEW");
        }
        if (hasFailedAgentRun(projectId)) {
            flags.add("AGENT_FAILURE");
        }
        return flags;
    }

    private boolean hasFailedAgentRun(UUID projectId) {
        return count(
                """
                        SELECT COUNT(*)
                        FROM agent_executions
                        WHERE project_id = ?
                          AND status <> 'SUCCEEDED'
                        """,
                projectId
        ) > 0;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private List<String> extractMentions(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> mentions = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher matcher = MENTION_PATTERN.matcher(body);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return List.copyOf(mentions);
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split("\\n---\\n"))
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record StoredProjectRoomArtifact(
            ProjectRoomArtifact artifact,
            String storageBucket,
            String storageObjectKey,
            String storageVersionId
    ) {
    }
}
