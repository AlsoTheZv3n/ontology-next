-- V25: Entity Resolution — persistent duplicate-candidate tracking
--
-- Stores detected potential duplicates with confidence scores.
-- < 0.75 -> not stored (noise)
-- 0.75 - 0.95 -> PENDING (human review)
-- > 0.95 -> AUTO_MERGED (no review needed)

CREATE TABLE resolution_decisions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    object_type_id  UUID NOT NULL REFERENCES object_types(id) ON DELETE CASCADE,
    candidate_a_id  UUID NOT NULL REFERENCES ontology_objects(id) ON DELETE CASCADE,
    candidate_b_id  UUID NOT NULL REFERENCES ontology_objects(id) ON DELETE CASCADE,
    match_type      VARCHAR(20) NOT NULL CHECK (match_type IN ('EXACT','FUZZY','SEMANTIC')),
    confidence      NUMERIC(4,3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    features        JSONB NOT NULL DEFAULT '{}'::jsonb,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','APPROVED','REJECTED','AUTO_MERGED','EXPIRED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ,
    resolved_by     VARCHAR(255),
    UNIQUE (tenant_id, candidate_a_id, candidate_b_id)
);

CREATE INDEX idx_resolution_pending ON resolution_decisions(tenant_id, status)
    WHERE status = 'PENDING';
CREATE INDEX idx_resolution_confidence ON resolution_decisions(tenant_id, confidence DESC);

-- RLS policy — consistent with V10/V24 (no OR NULL fallback)
ALTER TABLE resolution_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE resolution_decisions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_resolution_decisions ON resolution_decisions
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
