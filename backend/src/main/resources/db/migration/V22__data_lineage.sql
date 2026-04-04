CREATE TABLE property_lineage (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    object_id UUID NOT NULL REFERENCES ontology_objects(id) ON DELETE CASCADE,
    property_name VARCHAR(100) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(255),
    source_name VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    changed_by VARCHAR(255),
    changed_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_lineage_object ON property_lineage(object_id, property_name);
