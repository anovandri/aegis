CREATE TABLE knowledge_chunks (
    chunk_id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    document_version INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,
    text TEXT NOT NULL,
    embedding_model VARCHAR(200) NOT NULL,
    embedding TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_knowledge_chunks_document_version
        FOREIGN KEY (document_id, document_version)
        REFERENCES knowledge_document_versions (document_id, version)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_knowledge_chunks_document_version_index
    ON knowledge_chunks (document_id, document_version, chunk_index);

CREATE INDEX idx_knowledge_chunks_document_version
    ON knowledge_chunks (document_id, document_version);
