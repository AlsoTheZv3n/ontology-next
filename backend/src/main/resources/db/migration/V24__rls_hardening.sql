-- V24: RLS Hardening — remove dangerous NULL-fallback from all tenant-isolation policies.
--
-- Before this migration, policies were: USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
-- That meant: any connection WITHOUT app.tenant_id set could see ALL data across tenants.
-- This migration tightens the policies so that missing app.tenant_id -> 0 rows returned.
--
-- The current_tenant_id() function returns a sentinel UUID (all zeros) when no tenant
-- is set, which never matches any real tenant_id, ensuring zero data leakage.
--
-- Application MUST set app.tenant_id on every connection (TenantAwareDataSource).
-- Flyway migrations continue to run as superuser (bypasses RLS).

-- Harden the helper function: return sentinel UUID when no tenant set
CREATE OR REPLACE FUNCTION current_tenant_id() RETURNS uuid AS $$
DECLARE
    v text := current_setting('app.tenant_id', true);
BEGIN
    IF v IS NULL OR v = '' THEN
        -- Sentinel UUID that matches no real tenant
        RETURN '00000000-0000-0000-0000-000000000000'::uuid;
    END IF;
    RETURN v::uuid;
EXCEPTION
    WHEN OTHERS THEN
        RETURN '00000000-0000-0000-0000-000000000000'::uuid;
END;
$$ LANGUAGE plpgsql STABLE;

-- Rebuild all tenant-isolation policies without the OR NULL fallback.
-- Each policy now also has WITH CHECK to prevent INSERT/UPDATE bypass.

DO $$
DECLARE
    tbl text;
    tables text[] := ARRAY[
        'object_types',
        'link_types',
        'ontology_objects',
        'object_links',
        'action_types',
        'action_log',
        'data_source_definitions',
        'sync_result_log',
        'schema_versions',
        'object_history',
        'agent_sessions',
        'agent_audit_log',
        'pending_approvals',
        'tenant_users'
    ];
    policy_name text;
BEGIN
    FOREACH tbl IN ARRAY tables LOOP
        policy_name := 'tenant_isolation_' || tbl;
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I', policy_name, tbl);
        EXECUTE format(
            'CREATE POLICY %I ON %I ' ||
            'USING (tenant_id = current_tenant_id()) ' ||
            'WITH CHECK (tenant_id = current_tenant_id())',
            policy_name, tbl
        );
    END LOOP;
END $$;

COMMENT ON FUNCTION current_tenant_id() IS
    'Returns current app.tenant_id or sentinel UUID 00000000... if not set. Never returns NULL.';
