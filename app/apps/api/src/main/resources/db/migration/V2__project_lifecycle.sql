CREATE TABLE projects (
    project_id UUID PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    business_owner VARCHAR(300) NOT NULL,
    domain VARCHAR(300) NOT NULL,
    current_state VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE artifact_versions (
    project_id UUID NOT NULL,
    artifact_id UUID NOT NULL,
    artifact_type VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    media_type VARCHAR(200) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    storage_bucket VARCHAR(200) NOT NULL,
    storage_object_key VARCHAR(1000) NOT NULL,
    storage_version_id VARCHAR(200),
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (artifact_id, version),
    CONSTRAINT fk_artifact_versions_project
        FOREIGN KEY (project_id)
        REFERENCES projects (project_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_artifact_versions_project_type_version
    ON artifact_versions (project_id, artifact_type, version);

CREATE TABLE agent_executions (
    execution_id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    workflow_state VARCHAR(100) NOT NULL,
    agent_name VARCHAR(200) NOT NULL,
    agent_version VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    model VARCHAR(200) NOT NULL,
    status VARCHAR(100) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    summary TEXT NOT NULL,
    CONSTRAINT fk_agent_executions_project
        FOREIGN KEY (project_id)
        REFERENCES projects (project_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_agent_executions_project_state
    ON agent_executions (project_id, workflow_state);
