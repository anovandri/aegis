CREATE TABLE knowledge_documents (
    document_id UUID PRIMARY KEY,
    source_id VARCHAR(200) NOT NULL UNIQUE,
    source_title VARCHAR(500) NOT NULL,
    source_type VARCHAR(100) NOT NULL,
    authority VARCHAR(100) NOT NULL,
    allowed_agents TEXT NOT NULL,
    workflow_states TEXT NOT NULL,
    tags TEXT NOT NULL,
    latest_version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE knowledge_document_versions (
    document_id UUID NOT NULL,
    version INTEGER NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    media_type VARCHAR(200) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    storage_bucket VARCHAR(200) NOT NULL,
    storage_object_key VARCHAR(1000) NOT NULL,
    storage_version_id VARCHAR(200),
    extracted_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (document_id, version),
    CONSTRAINT fk_knowledge_document_versions_document
        FOREIGN KEY (document_id)
        REFERENCES knowledge_documents (document_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_documents_agent_state
    ON knowledge_documents (source_type, authority);

CREATE INDEX idx_knowledge_document_versions_hash
    ON knowledge_document_versions (content_hash);
