-- Create IVFFlat vector index for cosine similarity search
-- This should ideally be run AFTER bulk data load for better index quality.
-- For initial setup, we create it with a small number of lists.
-- Adjust lists = sqrt(row_count) for production.

CREATE INDEX idx_embedding_vector ON document_embeddings
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
