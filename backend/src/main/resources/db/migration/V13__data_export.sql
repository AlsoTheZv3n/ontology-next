CREATE TABLE data_export_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    export_scope VARCHAR(50) NOT NULL DEFAULT 'FULL',
    file_path TEXT,
    file_size_bytes BIGINT,
    object_count INT DEFAULT 0,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
