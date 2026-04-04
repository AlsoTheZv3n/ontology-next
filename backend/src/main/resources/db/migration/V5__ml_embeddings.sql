-- Embedding-Spalte auf ontology_objects Tabelle
ALTER TABLE ontology_objects ADD COLUMN IF NOT EXISTS
    embedding vector(384);

-- HNSW Index fuer schnelle Approximate Nearest Neighbor Suche
CREATE INDEX idx_objects_embedding ON ontology_objects
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Embedding-Metadata Tracking
ALTER TABLE ontology_objects ADD COLUMN IF NOT EXISTS
    embedding_model VARCHAR(100);

ALTER TABLE ontology_objects ADD COLUMN IF NOT EXISTS
    embedded_at TIMESTAMPTZ;
