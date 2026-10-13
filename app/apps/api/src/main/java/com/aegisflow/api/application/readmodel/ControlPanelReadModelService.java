package com.aegisflow.api.application.readmodel;

import com.aegisflow.api.application.readmodel.ControlPanelReadModels.AgentRunDetail;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.AgentRunSummary;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.ArtifactSummary;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.AuditEvent;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.DashboardReadModel;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.JiraDraftDetail;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.JiraDraftSummary;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.OperationsHealth;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.ProjectSummary;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.ReviewQueueItem;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.StateCount;
import com.aegisflow.api.application.readmodel.ControlPanelReadModels.WorkflowOperation;
import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ControlPanelReadModelService {
    private final JdbcTemplate jdbcTemplate;

    public ControlPanelReadModelService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardReadModel dashboard() {
        return new DashboardReadModel(
                count("SELECT COUNT(*) FROM projects WHERE current_state <> 'JIRA_CREATED'"),
                count("SELECT COUNT(*) FROM human_reviews WHERE status <> 'DECIDED'"),
                count("SELECT COUNT(*) FROM human_reviews WHERE status <> 'DECIDED' AND review_type = 'TECHNICAL_REVIEW'"),
                count("SELECT COUNT(*) FROM human_reviews WHERE status <> 'DECIDED' AND review_type = 'PM_APPROVAL'"),
                count("SELECT COUNT(*) FROM projects WHERE current_state = 'HUMAN_PM_APPROVAL'"),
                count("SELECT COUNT(*) FROM projects WHERE current_state = 'JIRA_CREATED'"),
                projectsByState(),
                reviewQueue(Optional.empty(), Optional.empty(), Optional.empty(), 5),
                projects(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 5),
                agentRuns(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 5)
        );
    }

    public List<ProjectSummary> projects(Optional<WorkflowState> state, Optional<String> owner, Optional<String> domain, int limit) {
        return projects(state, owner, domain, Optional.empty(), Optional.empty(), Optional.empty(), limit);
    }

    public List<ProjectSummary> projects(
            Optional<WorkflowState> state,
            Optional<String> owner,
            Optional<String> domain,
            Optional<String> risk,
            Optional<Boolean> stale,
            Optional<ReviewStatus> reviewStatus,
            int limit
    ) {
        StringBuilder sql = new StringBuilder(
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
                        WHERE 1 = 1
                        """
        );
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        state.ifPresent(value -> {
            sql.append(" AND p.current_state = ?");
            args.add(value.name());
        });
        owner.filter(value -> !value.isBlank()).ifPresent(value -> {
            sql.append(" AND LOWER(p.business_owner) LIKE LOWER(?)");
            args.add("%" + value + "%");
        });
        domain.filter(value -> !value.isBlank()).ifPresent(value -> {
            sql.append(" AND LOWER(p.domain) LIKE LOWER(?)");
            args.add("%" + value + "%");
        });
        sql.append(
                """
                        GROUP BY p.project_id, p.name, p.business_owner, p.domain, p.current_state, p.created_at
                        ORDER BY p.created_at DESC
                        LIMIT ?
                        """
        );
        args.add(Math.max(limit, 100));

        return jdbcTemplate.query(sql.toString(), (resultSet, rowNumber) -> projectSummary(resultSet), args.toArray()).stream()
                .filter(project -> stale.map(value -> project.hasStaleArtifacts() == value).orElse(true))
                .filter(project -> risk.filter(value -> !value.isBlank())
                        .map(value -> project.riskFlags().stream().anyMatch(flag -> flag.equalsIgnoreCase(value) || flag.toLowerCase().contains(value.toLowerCase())))
                        .orElse(true))
                .filter(project -> reviewStatus.map(value -> project.pendingReview() != null && project.pendingReview().status() == value).orElse(true))
                .limit(limit)
                .toList();
    }

    public List<ArtifactSummary> projectArtifacts(UUID projectId) {
        return artifacts(projectId);
    }

    public Optional<ProjectSummary> project(UUID projectId) {
        return findProjectSummary(projectId);
    }

    public List<ReviewQueueItem> myReviewQueue(Optional<String> role, Optional<ReviewType> reviewType, Optional<ReviewStatus> status) {
        return reviewQueue(status, reviewType, role, 100);
    }

    public List<AgentRunSummary> agentRuns(
            Optional<UUID> projectId,
            Optional<String> agentName,
            Optional<AgentExecutionStatus> status,
            Optional<WorkflowState> workflowState,
            int limit
    ) {
        StringBuilder sql = new StringBuilder(
                """
                        SELECT
                            ae.*,
                            p.name AS project_name
                        FROM agent_executions ae
                        JOIN projects p ON p.project_id = ae.project_id
                        WHERE 1 = 1
                        """
        );
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        projectId.ifPresent(value -> {
            sql.append(" AND ae.project_id = ?");
            args.add(value);
        });
        agentName.filter(value -> !value.isBlank()).ifPresent(value -> {
            sql.append(" AND LOWER(ae.agent_name) LIKE LOWER(?)");
            args.add("%" + value + "%");
        });
        status.ifPresent(value -> {
            sql.append(" AND ae.status = ?");
            args.add(value.name());
        });
        workflowState.ifPresent(value -> {
            sql.append(" AND ae.workflow_state = ?");
            args.add(value.name());
        });
        sql.append(" ORDER BY ae.started_at DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::agentRunSummary, args.toArray());
    }

    public Optional<AgentRunDetail> agentRunDetail(UUID executionId) {
        List<AgentRunSummary> executions = jdbcTemplate.query(
                """
                        SELECT ae.*, p.name AS project_name
                        FROM agent_executions ae
                        JOIN projects p ON p.project_id = ae.project_id
                        WHERE ae.execution_id = ?
                        """,
                this::agentRunSummary,
                executionId
        );
        return executions.stream().findFirst().map(execution -> new AgentRunDetail(
                execution,
                artifacts(execution.projectId()),
                reviewsForProject(execution.projectId())
        ));
    }

    public List<JiraDraftSummary> jiraDrafts() {
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
                        WHERE av.artifact_type = 'JIRA_PLAN'
                          AND av.version = (
                              SELECT MAX(inner_av.version)
                              FROM artifact_versions inner_av
                              WHERE inner_av.project_id = p.project_id
                                AND inner_av.artifact_type = 'JIRA_PLAN'
                          )
                        ORDER BY av.created_at DESC
                        """,
                this::jiraDraftSummary
        );
    }

    public Optional<JiraDraftSummary> jiraDraft(UUID projectId) {
        List<JiraDraftSummary> drafts = jdbcTemplate.query(
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
                this::jiraDraftSummary,
                projectId
        );
        return drafts.stream().findFirst();
    }

    public Optional<JiraDraftDetail> jiraDraftDetail(UUID projectId) {
        return jiraDraft(projectId).map(draft -> new JiraDraftDetail(
                draft,
                reviewsForProject(projectId).stream()
                        .filter(review -> review.reviewType() == ReviewType.PM_APPROVAL)
                        .toList(),
                auditEvents(Optional.of(projectId), 20)
        ));
    }

    public List<AuditEvent> auditEvents(Optional<UUID> projectId, int limit) {
        java.util.ArrayList<AuditEvent> events = new java.util.ArrayList<>();
        events.addAll(projectCreatedEvents(projectId, limit));
        events.addAll(artifactEvents(projectId, limit));
        events.addAll(agentExecutionEvents(projectId, limit));
        events.addAll(reviewEvents(projectId, limit));
        events.addAll(projectMessageEvents(projectId, limit));
        return events.stream()
                .sorted(java.util.Comparator.comparing(AuditEvent::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    public OperationsHealth operationsHealth() {
        Map<String, Long> agentStatus = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT status, COUNT(*) AS count FROM agent_executions GROUP BY status ORDER BY status",
                resultSet -> {
                    agentStatus.put(resultSet.getString("status"), resultSet.getLong("count"));
                }
        );
        return new OperationsHealth(
                count("SELECT COUNT(*) FROM projects WHERE current_state NOT IN ('JIRA_CREATED')"),
                count("SELECT COUNT(*) FROM projects WHERE current_state = 'HUMAN_TECHNICAL_REVIEW'"),
                count("SELECT COUNT(*) FROM projects WHERE current_state = 'HUMAN_PM_APPROVAL'"),
                count("SELECT COUNT(*) FROM projects WHERE current_state = 'JIRA_CREATED'"),
                count("SELECT COUNT(*) FROM agent_executions WHERE status <> 'SUCCEEDED'"),
                count("SELECT COUNT(*) FROM human_reviews WHERE status <> 'DECIDED'"),
                agentStatus,
                workflows(50)
        );
    }

    public List<WorkflowOperation> workflows(int limit) {
        return projects(Optional.empty(), Optional.empty(), Optional.empty(), limit).stream()
                .map(project -> new WorkflowOperation(
                        project.projectId(),
                        project.name(),
                        project.currentState(),
                        operationalStatus(project.currentState(), project.pendingReview()),
                        project.createdAt(),
                        project.pendingReview(),
                        project.latestAgentRun()
                ))
                .toList();
    }

    private List<StateCount> projectsByState() {
        return jdbcTemplate.query(
                """
                        SELECT current_state, COUNT(*) AS count
                        FROM projects
                        GROUP BY current_state
                        ORDER BY current_state
                        """,
                (resultSet, rowNumber) -> new StateCount(
                        WorkflowState.valueOf(resultSet.getString("current_state")),
                        resultSet.getLong("count")
                )
        );
    }

    private Optional<ProjectSummary> findProjectSummary(UUID projectId) {
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
                        (resultSet, rowNumber) -> projectSummary(resultSet),
                        projectId
                )
                .stream()
                .findFirst();
    }

    private ProjectSummary projectSummary(ResultSet resultSet) throws SQLException {
        UUID projectId = uuid(resultSet, "project_id");
        List<ArtifactSummary> projectArtifacts = artifacts(projectId);
        int staleArtifactCount = (int) projectArtifacts.stream().filter(ArtifactSummary::stale).count();
        List<String> riskFlags = riskFlags(projectId, staleArtifactCount);
        return new ProjectSummary(
                projectId,
                resultSet.getString("name"),
                resultSet.getString("business_owner"),
                resultSet.getString("domain"),
                WorkflowState.valueOf(resultSet.getString("current_state")),
                instant(resultSet, "created_at"),
                resultSet.getInt("artifact_count"),
                staleArtifactCount,
                staleArtifactCount > 0,
                riskFlags,
                nullableInstant(resultSet, "latest_artifact_at"),
                pendingReview(projectId).orElse(null),
                latestAgentRun(projectId).orElse(null)
        );
    }

    private List<ArtifactSummary> artifacts(UUID projectId) {
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
                this::artifactSummary,
                projectId
        );
    }

    private ArtifactSummary artifactSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ArtifactSummary(
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

    private List<String> riskFlags(UUID projectId, int staleArtifactCount) {
        java.util.ArrayList<String> flags = new java.util.ArrayList<>();
        if (staleArtifactCount > 0) {
            flags.add("STALE_ARTIFACTS");
        }
        long highPriorityPendingReviews = count(
                """
                        SELECT COUNT(*)
                        FROM human_reviews
                        WHERE project_id = ?
                          AND status <> 'DECIDED'
                          AND LOWER(priority) = 'high'
                        """,
                projectId
        );
        if (highPriorityPendingReviews > 0) {
            flags.add("HIGH_PRIORITY_REVIEW");
        }
        long failedAgentRuns = count(
                """
                        SELECT COUNT(*)
                        FROM agent_executions
                        WHERE project_id = ?
                          AND status <> 'SUCCEEDED'
                        """,
                projectId
        );
        if (failedAgentRuns > 0) {
            flags.add("AGENT_FAILURE");
        }
        return flags;
    }

    private Optional<ReviewQueueItem> pendingReview(UUID projectId) {
        return jdbcTemplate.query(
                        """
                                SELECT *
                                FROM human_reviews
                                WHERE project_id = ?
                                  AND status <> 'DECIDED'
                                ORDER BY due_at NULLS LAST, created_at DESC
                                LIMIT 1
                                """,
                        this::reviewQueueItem,
                        projectId
                )
                .stream()
                .findFirst();
    }

    public List<ReviewQueueItem> reviewsForProject(UUID projectId) {
        return jdbcTemplate.query(
                "SELECT * FROM human_reviews WHERE project_id = ? ORDER BY created_at DESC",
                this::reviewQueueItem,
                projectId
        );
    }

    private List<ReviewQueueItem> reviewQueue(Optional<ReviewStatus> status, Optional<ReviewType> reviewType, Optional<String> assigneeRole, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM human_reviews WHERE 1 = 1");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        status.ifPresent(value -> {
            sql.append(" AND status = ?");
            args.add(value.name());
        });
        if (status.isEmpty()) {
            sql.append(" AND status <> 'DECIDED'");
        }
        reviewType.ifPresent(value -> {
            sql.append(" AND review_type = ?");
            args.add(value.name());
        });
        assigneeRole.filter(value -> !value.isBlank()).ifPresent(value -> {
            sql.append(" AND assignee_role = ?");
            args.add(value);
        });
        sql.append(" ORDER BY due_at NULLS LAST, created_at DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::reviewQueueItem, args.toArray());
    }

    private ReviewQueueItem reviewQueueItem(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReviewQueueItem(
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

    private Optional<AgentRunSummary> latestAgentRun(UUID projectId) {
        return agentRuns(Optional.of(projectId), Optional.empty(), Optional.empty(), Optional.empty(), 1)
                .stream()
                .findFirst();
    }

    private AgentRunSummary agentRunSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AgentRunSummary(
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

    private JiraDraftSummary jiraDraftSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        WorkflowState currentState = WorkflowState.valueOf(resultSet.getString("current_state"));
        return new JiraDraftSummary(
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

    private List<AuditEvent> projectCreatedEvents(Optional<UUID> projectId, int limit) {
        String sql = projectId
                .map(value -> "SELECT * FROM projects WHERE project_id = ? ORDER BY created_at DESC LIMIT ?")
                .orElse("SELECT * FROM projects ORDER BY created_at DESC LIMIT ?");
        Object[] args = projectId.<Object[]>map(value -> new Object[]{value, limit}).orElseGet(() -> new Object[]{limit});
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new AuditEvent(
                "project-" + uuid(resultSet, "project_id"),
                "PROJECT_CREATED",
                "system",
                uuid(resultSet, "project_id"),
                resultSet.getString("name"),
                "PROJECT",
                uuid(resultSet, "project_id").toString(),
                "Project created in state " + resultSet.getString("current_state"),
                instant(resultSet, "created_at")
        ), args);
    }

    private List<AuditEvent> artifactEvents(Optional<UUID> projectId, int limit) {
        String sql = projectId
                .map(value -> """
                        SELECT av.*, p.name AS project_name
                        FROM artifact_versions av
                        JOIN projects p ON p.project_id = av.project_id
                        WHERE av.project_id = ?
                        ORDER BY av.created_at DESC
                        LIMIT ?
                        """)
                .orElse("""
                        SELECT av.*, p.name AS project_name
                        FROM artifact_versions av
                        JOIN projects p ON p.project_id = av.project_id
                        ORDER BY av.created_at DESC
                        LIMIT ?
                        """);
        Object[] args = projectId.<Object[]>map(value -> new Object[]{value, limit}).orElseGet(() -> new Object[]{limit});
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new AuditEvent(
                "artifact-" + uuid(resultSet, "artifact_id") + "-v" + resultSet.getInt("version"),
                "ARTIFACT_VERSION_CREATED",
                "system",
                uuid(resultSet, "project_id"),
                resultSet.getString("project_name"),
                "ARTIFACT",
                uuid(resultSet, "artifact_id") + ":" + resultSet.getInt("version"),
                resultSet.getString("artifact_type") + " v" + resultSet.getInt("version") + " stored as " + resultSet.getString("file_name"),
                instant(resultSet, "created_at")
        ), args);
    }

    private List<AuditEvent> agentExecutionEvents(Optional<UUID> projectId, int limit) {
        String sql = projectId
                .map(value -> """
                        SELECT ae.*, p.name AS project_name
                        FROM agent_executions ae
                        JOIN projects p ON p.project_id = ae.project_id
                        WHERE ae.project_id = ?
                        ORDER BY ae.completed_at DESC
                        LIMIT ?
                        """)
                .orElse("""
                        SELECT ae.*, p.name AS project_name
                        FROM agent_executions ae
                        JOIN projects p ON p.project_id = ae.project_id
                        ORDER BY ae.completed_at DESC
                        LIMIT ?
                        """);
        Object[] args = projectId.<Object[]>map(value -> new Object[]{value, limit}).orElseGet(() -> new Object[]{limit});
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new AuditEvent(
                "agent-" + uuid(resultSet, "execution_id"),
                "AGENT_EXECUTION_" + resultSet.getString("status"),
                resultSet.getString("agent_name"),
                uuid(resultSet, "project_id"),
                resultSet.getString("project_name"),
                "AGENT_EXECUTION",
                uuid(resultSet, "execution_id").toString(),
                resultSet.getString("summary"),
                instant(resultSet, "completed_at")
        ), args);
    }

    private List<AuditEvent> reviewEvents(Optional<UUID> projectId, int limit) {
        String sql = projectId
                .map(value -> """
                        SELECT hr.*, p.name AS project_name
                        FROM human_reviews hr
                        JOIN projects p ON p.project_id = hr.project_id
                        WHERE hr.project_id = ?
                        ORDER BY hr.updated_at DESC
                        LIMIT ?
                        """)
                .orElse("""
                        SELECT hr.*, p.name AS project_name
                        FROM human_reviews hr
                        JOIN projects p ON p.project_id = hr.project_id
                        ORDER BY hr.updated_at DESC
                        LIMIT ?
                        """);
        Object[] args = projectId.<Object[]>map(value -> new Object[]{value, limit}).orElseGet(() -> new Object[]{limit});
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new AuditEvent(
                "review-" + uuid(resultSet, "review_id") + "-" + resultSet.getString("status"),
                "HUMAN_REVIEW_" + resultSet.getString("status"),
                resultSet.getString("assignee") == null ? resultSet.getString("assignee_role") : resultSet.getString("assignee"),
                uuid(resultSet, "project_id"),
                resultSet.getString("project_name"),
                "HUMAN_REVIEW",
                uuid(resultSet, "review_id").toString(),
                resultSet.getString("title"),
                instant(resultSet, "updated_at")
        ), args);
    }

    private List<AuditEvent> projectMessageEvents(Optional<UUID> projectId, int limit) {
        String sql = projectId
                .map(value -> """
                        SELECT pm.*, p.name AS project_name
                        FROM project_messages pm
                        JOIN projects p ON p.project_id = pm.project_id
                        WHERE pm.project_id = ?
                        ORDER BY pm.created_at DESC
                        LIMIT ?
                        """)
                .orElse("""
                        SELECT pm.*, p.name AS project_name
                        FROM project_messages pm
                        JOIN projects p ON p.project_id = pm.project_id
                        ORDER BY pm.created_at DESC
                        LIMIT ?
                        """);
        Object[] args = projectId.<Object[]>map(value -> new Object[]{value, limit}).orElseGet(() -> new Object[]{limit});
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new AuditEvent(
                "message-" + uuid(resultSet, "message_id"),
                "PROJECT_MESSAGE_ADDED",
                resultSet.getString("actor"),
                uuid(resultSet, "project_id"),
                resultSet.getString("project_name"),
                "PROJECT_MESSAGE",
                uuid(resultSet, "message_id").toString(),
                resultSet.getString("body"),
                instant(resultSet, "created_at")
        ), args);
    }

    private String operationalStatus(WorkflowState state, ReviewQueueItem pendingReview) {
        if (pendingReview != null) {
            return "WAITING_FOR_" + pendingReview.reviewType().name();
        }
        if (state == WorkflowState.JIRA_CREATED) {
            return "COMPLETED";
        }
        return "RUNNING";
    }

    private long count(String sql) {
        return count(sql, new Object[]{});
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
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
}
