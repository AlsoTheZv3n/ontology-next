-- Ontology Object Instances (die eigentlichen Daten)
CREATE TABLE ontology_objects (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    object_type_id  UUID NOT NULL REFERENCES object_types(id),
    properties      JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_ontology_objects_type ON ontology_objects(object_type_id);
CREATE INDEX idx_ontology_objects_properties ON ontology_objects USING GIN(properties);

-- Object Links (Instanz-Beziehungen)
CREATE TABLE object_links (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    link_type_id    UUID NOT NULL REFERENCES link_types(id),
    source_id       UUID NOT NULL REFERENCES ontology_objects(id) ON DELETE CASCADE,
    target_id       UUID NOT NULL REFERENCES ontology_objects(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(link_type_id, source_id, target_id)
);

CREATE INDEX idx_object_links_source ON object_links(source_id);
CREATE INDEX idx_object_links_target ON object_links(target_id);

-- Action Types
CREATE TABLE action_types (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    api_name                VARCHAR(100) UNIQUE NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    target_object_type_id   UUID REFERENCES object_types(id),
    validation_rules        JSONB DEFAULT '[]',
    requires_approval       BOOLEAN DEFAULT FALSE,
    side_effects            JSONB DEFAULT '[]',
    description             TEXT,
    created_at              TIMESTAMPTZ DEFAULT NOW()
);

-- Action Log (Audit Trail)
CREATE TABLE action_log (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    action_type_id  UUID NOT NULL REFERENCES action_types(id),
    object_id       UUID,
    performed_by    VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    before_state    JSONB,
    after_state     JSONB,
    params          JSONB,
    error_message   TEXT,
    performed_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_action_log_object ON action_log(object_id);
CREATE INDEX idx_action_log_performed_at ON action_log(performed_at DESC);
CREATE INDEX idx_action_log_action_type ON action_log(action_type_id);
