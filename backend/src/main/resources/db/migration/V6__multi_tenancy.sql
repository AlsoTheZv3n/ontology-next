-- Tenants table
CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    api_name        VARCHAR(100) UNIQUE NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    plan            VARCHAR(50) DEFAULT 'FREE',
    is_active       BOOLEAN DEFAULT TRUE,
    max_object_types INT DEFAULT 10,
    max_objects      INT DEFAULT 10000,
    max_connectors   INT DEFAULT 3,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Tenant users table
CREATE TABLE tenant_users (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    email       VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role        VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(tenant_id, email)
);

CREATE INDEX idx_tenant_users_email ON tenant_users(email);

-- Create a default tenant for existing data
INSERT INTO tenants (id, api_name, display_name, plan, max_object_types, max_objects, max_connectors)
VALUES ('00000000-0000-0000-0000-000000000001', 'default', 'Default Tenant', 'ENTERPRISE', 1000, 1000000, 100);

-- Add tenant_id to all ontology tables
ALTER TABLE object_types ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE link_types ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE ontology_objects ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE object_links ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE action_types ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE action_log ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE data_source_definitions ADD COLUMN tenant_id UUID REFERENCES tenants(id);
ALTER TABLE sync_result_log ADD COLUMN tenant_id UUID REFERENCES tenants(id);

-- Backfill existing data with default tenant
UPDATE object_types SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE link_types SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE ontology_objects SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE object_links SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE action_types SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE action_log SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE data_source_definitions SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
UPDATE sync_result_log SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;

-- Indexes
CREATE INDEX idx_object_types_tenant ON object_types(tenant_id);
CREATE INDEX idx_link_types_tenant ON link_types(tenant_id);
CREATE INDEX idx_ontology_objects_tenant ON ontology_objects(tenant_id);
CREATE INDEX idx_object_links_tenant ON object_links(tenant_id);
CREATE INDEX idx_action_types_tenant ON action_types(tenant_id);
CREATE INDEX idx_action_log_tenant ON action_log(tenant_id);
CREATE INDEX idx_data_source_defs_tenant ON data_source_definitions(tenant_id);

-- Create default admin user (password: admin123 - BCrypt hash)
INSERT INTO tenant_users (id, tenant_id, email, password_hash, role)
VALUES ('00000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000001',
        'admin@nexo.ai',
        '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
        'OWNER');
