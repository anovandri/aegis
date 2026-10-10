package com.aegisflow.api.infrastructure.review;

import com.aegisflow.api.domain.HumanDecision;
import com.aegisflow.api.domain.HumanReview;
import com.aegisflow.api.domain.HumanReviewDecision;
import com.aegisflow.api.domain.ReviewStatus;
import com.aegisflow.api.domain.ReviewType;
import com.aegisflow.api.domain.WorkflowState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcReviewRepository implements ReviewRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcReviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveReview(HumanReview review) {
        jdbcTemplate.update(
                """
                        INSERT INTO human_reviews (
                            review_id,
                            project_id,
                            project_name,
                            review_type,
                            status,
                            workflow_state,
                            title,
                            summary,
                            assignee_role,
                            assignee,
                            priority,
                            due_at,
                            artifact_summary,
                            blocking_questions,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                review.reviewId(),
                review.projectId(),
                review.projectName(),
                review.reviewType().name(),
                review.status().name(),
                review.workflowState().name(),
                review.title(),
                review.summary(),
                review.assigneeRole(),
                review.assignee(),
                review.priority(),
                timestamp(review.dueAt()),
                review.artifactSummary(),
                join(review.blockingQuestions()),
                Timestamp.from(review.createdAt()),
                Timestamp.from(review.updatedAt())
        );
    }

    @Override
    public Optional<HumanReview> findReview(UUID reviewId) {
        return jdbcTemplate.query(
                        "SELECT * FROM human_reviews WHERE review_id = ?",
                        reviewMapper(),
                        reviewId
                )
                .stream()
                .findFirst();
    }

    @Override
    public Optional<HumanReview> findOpenReview(UUID projectId, ReviewType reviewType) {
        return jdbcTemplate.query(
                        """
                                SELECT *
                                FROM human_reviews
                                WHERE project_id = ?
                                  AND review_type = ?
                                  AND status <> 'DECIDED'
                                ORDER BY created_at DESC
                                LIMIT 1
                                """,
                        reviewMapper(),
                        projectId,
                        reviewType.name()
                )
                .stream()
                .findFirst();
    }

    @Override
    public List<HumanReview> findReviews(Optional<ReviewStatus> status, Optional<ReviewType> reviewType, Optional<String> assigneeRole) {
        StringBuilder sql = new StringBuilder("SELECT * FROM human_reviews WHERE 1 = 1");
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        status.ifPresent(value -> {
            sql.append(" AND status = ?");
            args.add(value.name());
        });
        reviewType.ifPresent(value -> {
            sql.append(" AND review_type = ?");
            args.add(value.name());
        });
        assigneeRole.ifPresent(value -> {
            sql.append(" AND assignee_role = ?");
            args.add(value);
        });
        sql.append(" ORDER BY due_at NULLS LAST, created_at DESC");
        return jdbcTemplate.query(sql.toString(), reviewMapper(), args.toArray());
    }

    @Override
    public void updateAssignment(UUID reviewId, ReviewStatus status, String assignee, Instant dueAt, Instant updatedAt) {
        jdbcTemplate.update(
                """
                        UPDATE human_reviews
                        SET status = ?,
                            assignee = ?,
                            due_at = ?,
                            updated_at = ?
                        WHERE review_id = ?
                        """,
                status.name(),
                assignee,
                timestamp(dueAt),
                Timestamp.from(updatedAt),
                reviewId
        );
    }

    @Override
    public void markDecided(UUID reviewId, Instant updatedAt) {
        jdbcTemplate.update(
                """
                        UPDATE human_reviews
                        SET status = 'DECIDED',
                            updated_at = ?
                        WHERE review_id = ?
                        """,
                Timestamp.from(updatedAt),
                reviewId
        );
    }

    @Override
    public void saveDecision(HumanReviewDecision decision) {
        jdbcTemplate.update(
                """
                        INSERT INTO human_review_decisions (
                            decision_id,
                            review_id,
                            project_id,
                            decision,
                            actor,
                            correction,
                            clarification_questions,
                            rejection_reason,
                            previous_state,
                            next_state,
                            generated_artifact_id,
                            generated_artifact_version,
                            created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                decision.decisionId(),
                decision.reviewId(),
                decision.projectId(),
                decision.decision().name(),
                decision.actor(),
                decision.correction(),
                join(decision.clarificationQuestions()),
                decision.rejectionReason(),
                decision.previousState().name(),
                decision.nextState().name(),
                decision.generatedArtifactId(),
                decision.generatedArtifactVersion(),
                Timestamp.from(decision.createdAt())
        );
    }

    @Override
    public List<HumanReviewDecision> findDecisions(UUID reviewId) {
        return jdbcTemplate.query(
                "SELECT * FROM human_review_decisions WHERE review_id = ? ORDER BY created_at DESC",
                decisionMapper(),
                reviewId
        );
    }

    private RowMapper<HumanReview> reviewMapper() {
        return (resultSet, rowNumber) -> new HumanReview(
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
                instant(resultSet, "due_at"),
                resultSet.getString("artifact_summary"),
                split(resultSet.getString("blocking_questions")),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at")
        );
    }

    private RowMapper<HumanReviewDecision> decisionMapper() {
        return (resultSet, rowNumber) -> new HumanReviewDecision(
                uuid(resultSet, "decision_id"),
                uuid(resultSet, "review_id"),
                uuid(resultSet, "project_id"),
                HumanDecision.valueOf(resultSet.getString("decision")),
                resultSet.getString("actor"),
                resultSet.getString("correction"),
                split(resultSet.getString("clarification_questions")),
                resultSet.getString("rejection_reason"),
                WorkflowState.valueOf(resultSet.getString("previous_state")),
                WorkflowState.valueOf(resultSet.getString("next_state")),
                nullableUuid(resultSet, "generated_artifact_id"),
                nullableInteger(resultSet, "generated_artifact_version"),
                instant(resultSet, "created_at")
        );
    }

    private String join(List<String> values) {
        return String.join("\n---\n", values == null ? List.of() : values);
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\n---\\n"))
                .filter(item -> !item.isBlank())
                .toList();
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, UUID.class);
    }

    private UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : resultSet.getObject(column, UUID.class);
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
