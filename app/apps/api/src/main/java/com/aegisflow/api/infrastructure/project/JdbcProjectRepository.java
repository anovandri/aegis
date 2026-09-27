package com.aegisflow.api.infrastructure.project;

import com.aegisflow.api.domain.AgentExecution;
import com.aegisflow.api.domain.AgentExecutionStatus;
import com.aegisflow.api.domain.ArtifactType;
import com.aegisflow.api.domain.ArtifactVersion;
import com.aegisflow.api.domain.Project;
import com.aegisflow.api.domain.WorkflowState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProjectRepository implements ProjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveProject(Project project) {
        jdbcTemplate.update(
                """
                        INSERT INTO projects (
                            project_id,
                            name,
                            business_owner,
                            domain,
                            current_state,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                project.projectId(),
                project.name(),
                project.businessOwner(),
                project.domain(),
                project.currentState().name(),
                Timestamp.from(project.createdAt())
        );
    }

    @Override
    public Optional<Project> findProject(UUID projectId) {
        List<Project> projects = jdbcTemplate.query(
                """
                        SELECT project_id, name, business_owner, domain, current_state, created_at
                        FROM projects
                        WHERE project_id = ?
                        """,
                projectMapper(),
                projectId
        );
        return projects.stream().findFirst();
    }

    @Override
    public void updateProjectState(UUID projectId, WorkflowState nextState) {
        jdbcTemplate.update(
                "UPDATE projects SET current_state = ? WHERE project_id = ?",
                nextState.name(),
                projectId
        );
    }

    @Override
    public void saveArtifactVersion(UUID projectId, ArtifactVersion artifactVersion) {
        jdbcTemplate.update(
                """
                        INSERT INTO artifact_versions (
                            project_id,
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
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                projectId,
                artifactVersion.artifactId(),
                artifactVersion.artifactType().name(),
                artifactVersion.version(),
                artifactVersion.fileName(),
                artifactVersion.mediaType(),
                artifactVersion.sizeBytes(),
                artifactVersion.contentHash(),
                artifactVersion.storageBucket(),
                artifactVersion.storageObjectKey(),
                artifactVersion.storageVersionId(),
                Timestamp.from(artifactVersion.createdAt())
        );
    }

    @Override
    public List<ArtifactVersion> findArtifactVersions(UUID projectId) {
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
                            created_at
                        FROM artifact_versions
                        WHERE project_id = ?
                        ORDER BY artifact_type, version
                        """,
                artifactMapper(),
                projectId
        );
    }

    @Override
    public Optional<ArtifactVersion> latestArtifact(UUID projectId, ArtifactType artifactType) {
        List<ArtifactVersion> artifacts = jdbcTemplate.query(
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
                            created_at
                        FROM artifact_versions
                        WHERE project_id = ? AND artifact_type = ?
                        ORDER BY version DESC
                        LIMIT 1
                        """,
                artifactMapper(),
                projectId,
                artifactType.name()
        );
        return artifacts.stream().findFirst();
    }

    @Override
    public void saveAgentExecution(AgentExecution execution) {
        jdbcTemplate.update(
                """
                        INSERT INTO agent_executions (
                            execution_id,
                            project_id,
                            workflow_state,
                            agent_name,
                            agent_version,
                            prompt_version,
                            model,
                            status,
                            confidence,
                            input_tokens,
                            output_tokens,
                            started_at,
                            completed_at,
                            summary
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                execution.executionId(),
                execution.projectId(),
                execution.workflowState().name(),
                execution.agentName(),
                execution.agentVersion(),
                execution.promptVersion(),
                execution.model(),
                execution.status().name(),
                execution.confidence(),
                execution.inputTokens(),
                execution.outputTokens(),
                Timestamp.from(execution.startedAt()),
                Timestamp.from(execution.completedAt()),
                execution.summary()
        );
    }

    private RowMapper<Project> projectMapper() {
        return (resultSet, rowNumber) -> new Project(
                uuid(resultSet, "project_id"),
                resultSet.getString("name"),
                resultSet.getString("business_owner"),
                resultSet.getString("domain"),
                WorkflowState.valueOf(resultSet.getString("current_state")),
                instant(resultSet, "created_at")
        );
    }

    private RowMapper<ArtifactVersion> artifactMapper() {
        return (resultSet, rowNumber) -> new ArtifactVersion(
                uuid(resultSet, "artifact_id"),
                ArtifactType.valueOf(resultSet.getString("artifact_type")),
                resultSet.getInt("version"),
                resultSet.getString("file_name"),
                resultSet.getString("media_type"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("content_hash"),
                resultSet.getString("storage_bucket"),
                resultSet.getString("storage_object_key"),
                resultSet.getString("storage_version_id"),
                instant(resultSet, "created_at")
        );
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
}
