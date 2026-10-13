CREATE TABLE project_messages (
    message_id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    actor VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    anchor_type VARCHAR(100),
    anchor_id VARCHAR(300),
    mentions TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_project_messages_project
        FOREIGN KEY (project_id)
        REFERENCES projects (project_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_project_messages_project_created
    ON project_messages (project_id, created_at);
