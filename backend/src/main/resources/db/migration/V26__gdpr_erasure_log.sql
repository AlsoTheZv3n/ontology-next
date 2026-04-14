-- V26: GDPR erasure audit log.
-- Records every right-to-be-forgotten request: who initiated it, which objects
-- were anonymized, when it completed. Row-level security keeps the log scoped
-- to the tenant that initiated the erasure.
--
-- subject_email_hash lets us detect idempotent re-runs (second erasure of the
-- same address returns 409) without storing the address in plaintext after
-- completion -- once the request succeeds the original subject_email column
-- may be cleared by a separate process.

CREATE TABLE gdpr_erasure_log (
    id                       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id                UUID NOT NULL REFERENCES tenants(id),
    subject_email            TEXT NOT NULL,
    subject_email_hash       TEXT NOT NULL,
    initiated_by             TEXT,
    reason                   TEXT,
    affected_objects         JSONB NOT NULL DEFAULT '[]'::jsonb,
    affected_count           INT  NOT NULL DEFAULT 0,
    audit_events_anonymized  INT  NOT NULL DEFAULT 0,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','COMPLETED','FAILED')),
    initiated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at             TIMESTAMPTZ
);

CREATE INDEX idx_gdpr_tenant_hash ON gdpr_erasure_log(tenant_id, subject_email_hash);
CREATE INDEX idx_gdpr_tenant_status ON gdpr_erasure_log(tenant_id, status);

ALTER TABLE gdpr_erasure_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE gdpr_erasure_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_gdpr_log ON gdpr_erasure_log
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
