CREATE TABLE knowledge_source_connections (
    connection_id UUID PRIMARY KEY,
    source_id VARCHAR(200) NOT NULL,
    adapter_type VARCHAR(100) NOT NULL,
    connection_name VARCHAR(300) NOT NULL,
    connection_status VARCHAR(100) NOT NULL,
    resource_locator TEXT NOT NULL,
    auth_type VARCHAR(100) NOT NULL,
    credential_ref VARCHAR(500),
    config_json TEXT NOT NULL,
    last_checked_at TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_source_connections_source
        FOREIGN KEY (source_id)
        REFERENCES knowledge_sources (source_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_source_connections_source
    ON knowledge_source_connections (source_id, created_at);

CREATE TABLE knowledge_source_resources (
    resource_id UUID PRIMARY KEY,
    connection_id UUID NOT NULL,
    external_id VARCHAR(500) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    uri TEXT NOT NULL,
    version_ref VARCHAR(300),
    content_hash VARCHAR(128),
    status VARCHAR(100) NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_source_resources_connection
        FOREIGN KEY (connection_id)
        REFERENCES knowledge_source_connections (connection_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_knowledge_source_resources_external
        UNIQUE (connection_id, external_id)
);

CREATE INDEX idx_knowledge_source_resources_connection
    ON knowledge_source_resources (connection_id, last_seen_at);
