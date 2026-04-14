-- V27: OAuth2 connector scaffolding.
-- connector_oauth_clients holds the per-connector OAuth app credentials (client
-- secret is always stored AES-256-GCM encrypted via CryptoService).
-- connector_oauth_states tracks outstanding authorize-redirect flows so the
-- callback can verify the state parameter (CSRF + replay protection, TTL 10 min).

CREATE TABLE connector_oauth_clients (
    connector_id       VARCHAR(100) PRIMARY KEY,
    client_id          TEXT NOT NULL,
    client_secret_enc  TEXT NOT NULL,
    auth_url           TEXT NOT NULL,
    token_url          TEXT NOT NULL,
    scopes             TEXT NOT NULL,
    redirect_uri       TEXT NOT NULL,
    extra_params       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE connector_oauth_states (
    state              TEXT PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id            UUID REFERENCES users(id),
    connector_id       VARCHAR(100) NOT NULL,
    redirect_after     TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_at        TIMESTAMPTZ
);

CREATE INDEX idx_oauth_state_tenant_time
    ON connector_oauth_states(tenant_id, created_at);

ALTER TABLE connector_oauth_states ENABLE ROW LEVEL SECURITY;
ALTER TABLE connector_oauth_states FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_oauth_states ON connector_oauth_states
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
