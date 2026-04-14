package com.nexoai.ontology.config;

import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Wraps an existing DataSource and applies `SET app.tenant_id = '<uuid>'` on every
 * connection borrow. This is the runtime counterpart to the PostgreSQL RLS policies
 * defined in V10/V24 migrations.
 *
 * Flow:
 * 1. HTTP request arrives, TenantInterceptor/JwtAuthFilter sets TenantContext
 * 2. Service/Repo requests a DB connection
 * 3. This wrapper retrieves connection from pool and runs SET app.tenant_id
 * 4. All subsequent queries on that connection are filtered by RLS policies
 * 5. Connection returned to pool; next borrow re-applies current tenant
 *
 * When TenantContext has no tenant set (e.g., during app bootstrap or
 * non-tenant operations), we RESET the setting — current_tenant_id() then
 * returns sentinel UUID 00000000-..., which matches no real tenant → zero rows.
 */
@Slf4j
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection c = super.getConnection();
        applyTenantContext(c);
        return c;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection c = super.getConnection(username, password);
        applyTenantContext(c);
        return c;
    }

    private void applyTenantContext(Connection c) throws SQLException {
        UUID tid = TenantContext.getTenantIdOrNull();
        try (Statement s = c.createStatement()) {
            if (tid != null) {
                // SET (not SET LOCAL) persists across transactions within the connection,
                // but connections are reset when returned to Hikari pool.
                s.execute("SET app.tenant_id = '" + tid + "'");
                log.trace("Connection bound to tenant {}", tid);
            } else {
                s.execute("RESET app.tenant_id");
                log.trace("Connection without tenant context (sentinel UUID applied)");
            }
        }
    }
}
