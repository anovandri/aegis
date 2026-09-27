CREATE TABLE knowledge_sources (
    source_id VARCHAR(200) PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    source_type VARCHAR(100) NOT NULL,
    authority VARCHAR(100) NOT NULL,
    status VARCHAR(100) NOT NULL,
    owner_team VARCHAR(300) NOT NULL,
    enabled BOOLEAN NOT NULL,
    freshness_sla_hours INTEGER NOT NULL,
    sync_mode VARCHAR(100) NOT NULL,
    sensitivity_policy VARCHAR(100) NOT NULL,
    allowed_agents TEXT NOT NULL,
    workflow_states TEXT NOT NULL,
    tags TEXT NOT NULL,
    last_synced_at TIMESTAMP,
    review_due_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

INSERT INTO knowledge_sources (
    source_id,
    name,
    source_type,
    authority,
    status,
    owner_team,
    enabled,
    freshness_sla_hours,
    sync_mode,
    sensitivity_policy,
    allowed_agents,
    workflow_states,
    tags,
    last_synced_at,
    review_due_at,
    created_at
)
SELECT
    source_id,
    source_title,
    source_type,
    authority,
    'FRESH',
    'Unassigned',
    TRUE,
    720,
    'MANUAL_UPLOAD',
    'INTERNAL',
    allowed_agents,
    workflow_states,
    tags,
    created_at,
    NULL,
    created_at
FROM knowledge_documents
WHERE source_id NOT IN (SELECT source_id FROM knowledge_sources);

CREATE TABLE knowledge_sync_runs (
    sync_run_id UUID PRIMARY KEY,
    source_id VARCHAR(200) NOT NULL,
    status VARCHAR(100) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    records_changed INTEGER NOT NULL,
    error_message TEXT,
    CONSTRAINT fk_knowledge_sync_runs_source
        FOREIGN KEY (source_id)
        REFERENCES knowledge_sources (source_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_sync_runs_source_started
    ON knowledge_sync_runs (source_id, started_at);

CREATE TABLE knowledge_audit_events (
    event_id UUID PRIMARY KEY,
    source_id VARCHAR(200),
    action VARCHAR(100) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_knowledge_audit_events_source_created
    ON knowledge_audit_events (source_id, created_at);

CREATE TABLE knowledge_citation_usages (
    usage_id UUID PRIMARY KEY,
    agent_execution_id UUID,
    project_id UUID,
    source_id VARCHAR(200) NOT NULL,
    document_id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    retrieved_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_knowledge_citation_usages_source
    ON knowledge_citation_usages (source_id, retrieved_at);
