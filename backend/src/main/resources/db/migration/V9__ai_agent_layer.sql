-- Agent Sessions
CREATE TABLE agent_sessions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID REFERENCES tenants(id),
    created_by      VARCHAR(255),
    history         JSONB DEFAULT '[]',
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    last_active_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_agent_sessions_tenant ON agent_sessions(tenant_id);

-- Pending Approvals (HITL)
CREATE TABLE pending_approvals (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id              UUID REFERENCES agent_sessions(id),
    tenant_id               UUID REFERENCES tenants(id),
    action_type             VARCHAR(100) NOT NULL,
    object_id               UUID,
    params                  JSONB,
    human_readable_summary  TEXT,
    agent_reasoning         TEXT,
    status                  VARCHAR(20) DEFAULT 'PENDING',
    resolved_by             VARCHAR(255),
    resolved_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_pending_approvals_session ON pending_approvals(session_id);
CREATE INDEX idx_pending_approvals_status ON pending_approvals(status);

-- Agent Audit Log
CREATE TABLE agent_audit_log (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id      UUID REFERENCES agent_sessions(id),
    tenant_id       UUID REFERENCES tenants(id),
    user_message    TEXT NOT NULL,
    agent_response  TEXT,
    tool_calls      JSONB DEFAULT '[]',
    actions_executed JSONB DEFAULT '[]',
    performed_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_agent_audit_session ON agent_audit_log(session_id);
CREATE INDEX idx_agent_audit_tenant ON agent_audit_log(tenant_id);
