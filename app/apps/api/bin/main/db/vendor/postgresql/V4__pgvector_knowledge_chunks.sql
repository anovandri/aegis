CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE knowledge_chunks
    ADD COLUMN embedding_vector vector(64);

CREATE INDEX idx_knowledge_chunks_embedding_vector_cosine
    ON knowledge_chunks
    USING hnsw (embedding_vector vector_cosine_ops);
