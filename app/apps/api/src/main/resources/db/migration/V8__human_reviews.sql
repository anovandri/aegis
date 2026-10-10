CREATE TABLE human_reviews (
    review_id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    project_name VARCHAR(500) NOT NULL,
    review_type VARCHAR(100) NOT NULL,
    status VARCHAR(100) NOT NULL,
    workflow_state VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    summary TEXT NOT NULL,
    assignee_role VARCHAR(200) NOT NULL,
    assignee VARCHAR(300),
    priority VARCHAR(100) NOT NULL,
    due_at TIMESTAMP,
    artifact_summary TEXT NOT NULL,
    blocking_questions TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_human_reviews_project
        FOREIGN KEY (project_id)
        REFERENCES projects (project_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_human_reviews_status_type_due
    ON human_reviews (status, review_type, due_at);

CREATE INDEX idx_human_reviews_project_type_status
    ON human_reviews (project_id, review_type, status);

CREATE TABLE human_review_decisions (
    decision_id UUID PRIMARY KEY,
    review_id UUID NOT NULL,
    project_id UUID NOT NULL,
    decision VARCHAR(100) NOT NULL,
    actor VARCHAR(300) NOT NULL,
    correction TEXT,
    clarification_questions TEXT NOT NULL,
    rejection_reason TEXT,
    previous_state VARCHAR(100) NOT NULL,
    next_state VARCHAR(100) NOT NULL,
    generated_artifact_id UUID,
    generated_artifact_version INTEGER,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_human_review_decisions_review
        FOREIGN KEY (review_id)
        REFERENCES human_reviews (review_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_human_review_decisions_project
        FOREIGN KEY (project_id)
        REFERENCES projects (project_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_human_review_decisions_review_created
    ON human_review_decisions (review_id, created_at);
