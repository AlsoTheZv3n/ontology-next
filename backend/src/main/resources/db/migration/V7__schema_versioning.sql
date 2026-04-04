-- Schema Versionierung
CREATE TABLE schema_versions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    object_type_id  UUID NOT NULL REFERENCES object_types(id),
    tenant_id       UUID REFERENCES tenants(id),
    version         INT NOT NULL,
    schema_snapshot JSONB NOT NULL,
    change_summary  TEXT,
    is_breaking     BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      VARCHAR(255),
    UNIQUE(object_type_id, version)
);

CREATE INDEX idx_schema_versions_ot ON schema_versions(object_type_id, version DESC);

-- Schema Migrations (Transformationsregeln)
CREATE TABLE schema_migrations (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    object_type_id      UUID NOT NULL REFERENCES object_types(id),
    from_version        INT NOT NULL,
    to_version          INT NOT NULL,
    migration_type      VARCHAR(50) NOT NULL,
    source_property     VARCHAR(100),
    target_property     VARCHAR(100),
    transformation      JSONB,
    UNIQUE(object_type_id, from_version, to_version, source_property)
);

-- Object History (bi-temporal)
CREATE TABLE object_history (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    object_id       UUID NOT NULL REFERENCES ontology_objects(id) ON DELETE CASCADE,
    tenant_id       UUID REFERENCES tenants(id),
    schema_version  INT NOT NULL DEFAULT 1,
    properties      JSONB NOT NULL,
    tx_from         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    tx_to           TIMESTAMPTZ,
    valid_from      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_to        TIMESTAMPTZ,
    change_source   VARCHAR(100),
    changed_by      VARCHAR(255)
);

CREATE INDEX idx_obj_history_object ON object_history(object_id);
CREATE INDEX idx_obj_history_tx ON object_history(tx_from DESC);
CREATE INDEX idx_obj_history_valid ON object_history(valid_from DESC);

-- Add schema_version to ontology_objects
ALTER TABLE ontology_objects ADD COLUMN schema_version INT DEFAULT 1;
