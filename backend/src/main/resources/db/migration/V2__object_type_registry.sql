CREATE TABLE object_types (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    api_name        VARCHAR(100) UNIQUE NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    description     TEXT,
    icon            VARCHAR(50),
    color           VARCHAR(7),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE property_types (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    object_type_id  UUID NOT NULL REFERENCES object_types(id) ON DELETE CASCADE,
    api_name        VARCHAR(100) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    data_type       VARCHAR(20) NOT NULL,
    is_primary_key  BOOLEAN DEFAULT FALSE,
    is_required     BOOLEAN DEFAULT FALSE,
    is_indexed      BOOLEAN DEFAULT FALSE,
    default_value   TEXT,
    description     TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(object_type_id, api_name)
);

CREATE TABLE link_types (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    api_name                VARCHAR(100) UNIQUE NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    source_object_type_id   UUID NOT NULL REFERENCES object_types(id),
    target_object_type_id   UUID NOT NULL REFERENCES object_types(id),
    cardinality             VARCHAR(20) NOT NULL DEFAULT 'ONE_TO_MANY',
    description             TEXT,
    created_at              TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_property_types_object_type ON property_types(object_type_id);
CREATE INDEX idx_link_types_source ON link_types(source_object_type_id);
CREATE INDEX idx_link_types_target ON link_types(target_object_type_id);
