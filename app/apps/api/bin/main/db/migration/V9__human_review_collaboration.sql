CREATE TABLE human_review_comments (
    comment_id UUID PRIMARY KEY,
    review_id UUID NOT NULL,
    project_id UUID NOT NULL,
    actor VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    mentions TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_human_review_comments_review
        FOREIGN KEY (review_id)
        REFERENCES human_reviews (review_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_human_review_comments_project
        FOREIGN KEY (project_id)
        REFERENCES projects (project_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_human_review_comments_review_created
    ON human_review_comments (review_id, created_at);

CREATE TABLE human_review_attachments (
    attachment_id UUID PRIMARY KEY,
    review_id UUID NOT NULL,
    project_id UUID NOT NULL,
    actor VARCHAR(300) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    media_type VARCHAR(200) NOT NULL,
    storage_uri TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_human_review_attachments_review
        FOREIGN KEY (review_id)
        REFERENCES human_reviews (review_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_human_review_attachments_project
        FOREIGN KEY (project_id)
        REFERENCES projects (project_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_human_review_attachments_review_created
    ON human_review_attachments (review_id, created_at);

CREATE TABLE human_review_events (
    event_id UUID PRIMARY KEY,
    review_id UUID NOT NULL,
    project_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    actor VARCHAR(300) NOT NULL,
    message TEXT NOT NULL,
    metadata TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_human_review_events_review
        FOREIGN KEY (review_id)
        REFERENCES human_reviews (review_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_human_review_events_project
        FOREIGN KEY (project_id)
        REFERENCES projects (project_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_human_review_events_review_created
    ON human_review_events (review_id, created_at);
