-- V10: Row Level Security policies for multi-tenant isolation
-- Uses current_setting('app.tenant_id', true) so the application must SET this per-connection.
-- FORCE ROW LEVEL SECURITY ensures policies apply even to the table owner (superuser nexo).

-- Helper function to extract current tenant
CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS uuid AS $$
BEGIN
    RETURN current_setting('app.tenant_id', true)::uuid;
EXCEPTION
    WHEN OTHERS THEN RETURN NULL;
END;
$$ LANGUAGE plpgsql STABLE;

-- object_types
ALTER TABLE object_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_types FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_object_types ON object_types
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- link_types
ALTER TABLE link_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE link_types FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_link_types ON link_types
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- ontology_objects
ALTER TABLE ontology_objects ENABLE ROW LEVEL SECURITY;
ALTER TABLE ontology_objects FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_ontology_objects ON ontology_objects
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- object_links
ALTER TABLE object_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_links FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_object_links ON object_links
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- action_types
ALTER TABLE action_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE action_types FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_action_types ON action_types
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- action_log
ALTER TABLE action_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE action_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_action_log ON action_log
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- data_source_definitions
ALTER TABLE data_source_definitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_source_definitions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_data_source_definitions ON data_source_definitions
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- sync_result_log
ALTER TABLE sync_result_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_result_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_sync_result_log ON sync_result_log
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- schema_versions
ALTER TABLE schema_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE schema_versions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schema_versions ON schema_versions
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- schema_migrations: NO tenant_id column, skip RLS

-- object_history
ALTER TABLE object_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE object_history FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_object_history ON object_history
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- agent_sessions
ALTER TABLE agent_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_agent_sessions ON agent_sessions
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- agent_audit_log
ALTER TABLE agent_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_agent_audit_log ON agent_audit_log
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- pending_approvals
ALTER TABLE pending_approvals ENABLE ROW LEVEL SECURITY;
ALTER TABLE pending_approvals FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_pending_approvals ON pending_approvals
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- tenant_users
ALTER TABLE tenant_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_tenant_users ON tenant_users
    USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
