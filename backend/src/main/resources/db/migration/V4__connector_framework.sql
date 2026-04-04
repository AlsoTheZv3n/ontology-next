-- Data Source Definitions
CREATE TABLE data_source_definitions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    api_name            VARCHAR(100) UNIQUE NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    connector_type      VARCHAR(20) NOT NULL,
    config              JSONB NOT NULL,
    object_type_id      UUID REFERENCES object_types(id),
    column_mapping      JSONB DEFAULT '{}',
    sync_interval_cron  VARCHAR(100) DEFAULT '0 */15 * * * *',
    is_active           BOOLEAN DEFAULT TRUE,
    last_synced_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

-- Sync Result Log
CREATE TABLE sync_result_log (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    data_source_id      UUID NOT NULL REFERENCES data_source_definitions(id),
    status              VARCHAR(20) NOT NULL,
    objects_synced      INT DEFAULT 0,
    objects_created     INT DEFAULT 0,
    objects_updated     INT DEFAULT 0,
    objects_failed      INT DEFAULT 0,
    error_message       TEXT,
    started_at          TIMESTAMPTZ NOT NULL,
    finished_at         TIMESTAMPTZ
);

CREATE INDEX idx_sync_log_data_source ON sync_result_log(data_source_id);
CREATE INDEX idx_sync_log_started_at ON sync_result_log(started_at DESC);

-- Add external_id to ontology_objects for upsert support
ALTER TABLE ontology_objects ADD COLUMN external_id VARCHAR(255);
ALTER TABLE ontology_objects ADD COLUMN data_source_id UUID REFERENCES data_source_definitions(id);
CREATE INDEX idx_ontology_objects_external ON ontology_objects(external_id, data_source_id);
