package com.nexoai.ontology.config;

import com.nexoai.ontology.core.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantAwareDataSource — verifies that every connection borrow
 * issues the correct SET/RESET command for app.tenant_id, which drives PostgreSQL RLS.
 */
class TenantAwareDataSourceTest {

    private DataSource delegate;
    private Connection connection;
    private Statement statement;
    private TenantAwareDataSource wrapper;

    @BeforeEach
    void setUp() throws Exception {
        delegate = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        when(delegate.getConnection()).thenReturn(connection);
        when(delegate.getConnection(anyString(), anyString())).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        wrapper = new TenantAwareDataSource(delegate);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void borrows_connection_and_sets_tenant_id_when_context_has_tenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        Connection c = wrapper.getConnection();

        verify(delegate).getConnection();
        verify(statement).execute("SET app.tenant_id = '" + tenantId + "'");
        // Connection must be returned to caller
        assert c == connection;
    }

    @Test
    void resets_tenant_when_context_empty() throws Exception {
        // TenantContext.clear() already in setUp
        wrapper.getConnection();

        verify(statement).execute("RESET app.tenant_id");
    }

    @Test
    void overloaded_getConnection_with_credentials_also_sets_tenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        wrapper.getConnection("user", "pw");

        verify(delegate).getConnection("user", "pw");
        verify(statement).execute("SET app.tenant_id = '" + tenantId + "'");
    }

    @Test
    void closes_statement_after_setting() throws Exception {
        TenantContext.setTenantId(UUID.randomUUID());
        wrapper.getConnection();
        // try-with-resources in applyTenantContext ensures Statement.close()
        verify(statement).close();
    }
}
