CREATE TABLE knowledge_synced_resource_documents (
    resource_key VARCHAR(128) PRIMARY KEY,
    source_id VARCHAR(200) NOT NULL,
    connection_id UUID NOT NULL,
    external_id VARCHAR(500) NOT NULL,
    document_id UUID NOT NULL,
    last_content_hash VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_synced_resource_documents_source
        FOREIGN KEY (source_id)
        REFERENCES knowledge_sources (source_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_synced_resource_documents_connection
        FOREIGN KEY (connection_id)
        REFERENCES knowledge_source_connections (connection_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_knowledge_synced_resource_documents_document
        FOREIGN KEY (document_id)
        REFERENCES knowledge_documents (document_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_knowledge_synced_resource_documents_source
    ON knowledge_synced_resource_documents (source_id, updated_at);
