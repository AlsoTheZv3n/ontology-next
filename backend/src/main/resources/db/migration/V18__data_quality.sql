CREATE TABLE validation_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    object_type_id UUID NOT NULL REFERENCES object_types(id),
    property_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    rule_config JSONB DEFAULT '{}',
    error_message VARCHAR(500) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE data_quality_reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    object_type_id UUID NOT NULL REFERENCES object_types(id),
    total_objects INT NOT NULL,
    valid_objects INT NOT NULL,
    quality_score DECIMAL(5,2) NOT NULL,
    issues JSONB DEFAULT '[]',
    created_at TIMESTAMPTZ DEFAULT NOW()
);
