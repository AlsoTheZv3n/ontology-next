CREATE TABLE plans (
    id VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    max_object_types INT NOT NULL,
    max_objects INT NOT NULL,
    max_connectors INT NOT NULL,
    max_agent_calls_per_month INT NOT NULL,
    max_sync_runs_per_month INT NOT NULL,
    price_monthly_chf DECIMAL(10,2)
);

INSERT INTO plans VALUES
('FREE','Free',10,10000,3,100,500,0),
('STARTER','Starter',50,100000,10,1000,5000,49.00),
('PRO','Pro',200,1000000,50,10000,50000,199.00),
('ENTERPRISE','Enterprise',1000,10000000,500,100000,500000,NULL);

CREATE TABLE usage_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    event_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_usage_events_tenant_type ON usage_events(tenant_id, event_type, created_at DESC);
