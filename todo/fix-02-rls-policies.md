# Fix 02 — PostgreSQL Row-Level Security (RLS)

**Priorität:** 🔴 Kritisch  
**Aufwand:** Medium  
**Status:** 🔲 Offen

---

## Problem

`tenant_id` Spalte existiert auf allen Tabellen und wird befüllt — aber keine PostgreSQL RLS Policies aktiv. Das bedeutet: ein Bug im Application-Code (fehlender Filter) würde Tenant-Daten leaken.

---

## Files

```
backend/src/main/resources/db/migration/V10__rls_policies.sql   ← neu erstellen
backend/src/main/java/com/nexoai/ontology/config/JpaConfig.java
backend/src/main/java/com/nexoai/ontology/core/tenant/TenantAwareConnectionCustomizer.java  ← neu
docker/postgres/init.sql
```

---

## Fix: docker/postgres/init.sql

```sql
-- Haupt-DB erstellen (falls nicht schon vorhanden)
CREATE DATABASE nexo_ontology;

-- App-User (kein Superuser!)
-- Superuser kann RLS bypassen → daher separater App-User
CREATE ROLE nexo_app WITH LOGIN PASSWORD 'nexo_app_secret';
GRANT CONNECT ON DATABASE nexo_ontology TO nexo_app;

\c nexo_ontology

-- Extensions
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Rechte für App-User
GRANT USAGE ON SCHEMA public TO nexo_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO nexo_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO nexo_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nexo_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO nexo_app;
```

---

## Fix: V10__rls_policies.sql

```sql
-- ============================================================
-- ROW LEVEL SECURITY — alle Ontology-Tabellen absichern
-- ============================================================
-- Voraussetzung: App läuft als nexo_app (nicht als postgres)
-- TenantContext setzt: SET LOCAL app.tenant_id = '<uuid>'
-- ============================================================

-- Tabellen mit tenant_id
DO $$
DECLARE
    tbl TEXT;
    tables TEXT[] := ARRAY[
        'object_types',
        'property_types',
        'link_types',
        'objects',
        'links',
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
BEGIN
    FOREACH tbl IN ARRAY tables LOOP
        -- RLS aktivieren
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);

        -- FORCE: auch Table-Owner (nexo_admin) unterliegt RLS
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);

        -- SELECT/INSERT/UPDATE/DELETE Policy
        EXECUTE format($$
            CREATE POLICY tenant_isolation ON %I
            USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
            WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid)
        $$, tbl);
    END LOOP;
END $$;

-- Spezialfall: tenants Tabelle — kein tenant_id (ist die Root-Tabelle)
-- Kein RLS nötig, wird nur vom Super-Admin verwaltet

-- Spezialfall: sync_result_log hat tenant_id via JOIN auf data_source_definitions
-- Direktes tenant_id Feld einfügen falls noch nicht vorhanden
ALTER TABLE sync_result_log ADD COLUMN IF NOT EXISTS
    tenant_id UUID REFERENCES tenants(id);

-- Flyway Tabelle von RLS ausschliessen
-- (Flyway läuft als Superuser, nicht als nexo_app)
```

---

## Fix: TenantAwareConnectionCustomizer.java

```java
package com.nexoai.ontology.core.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Setzt das PostgreSQL Session-Setting app.tenant_id vor jeder Query.
 * Das ist die Basis für Row-Level Security Policies.
 *
 * Implementierung via DataSource-Proxy, der jede Connection abfängt
 * und SET LOCAL app.tenant_id ausführt.
 */
@Slf4j
public class TenantAwareDataSource extends HikariDataSource {

    public TenantAwareDataSource(HikariConfig config) {
        super(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        applyTenantContext(conn);
        return conn;
    }

    private void applyTenantContext(Connection conn) throws SQLException {
        try {
            UUID tenantId = TenantContext.getTenantIdOrNull();
            if (tenantId != null) {
                try (Statement stmt = conn.createStatement()) {
                    // SET LOCAL gilt nur für aktuelle Transaktion
                    stmt.execute("SET LOCAL app.tenant_id = '" + tenantId + "'");
                }
            } else {
                // Kein Tenant gesetzt: RLS blockiert alles (sichere Defaults)
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("SET LOCAL app.tenant_id = '00000000-0000-0000-0000-000000000000'");
                }
                log.debug("No tenant in context, RLS will block all rows");
            }
        } catch (IllegalStateException e) {
            // Kein Tenant-Context (z.B. Flyway-Migration, Admin-Job)
            // → kein SET, Superuser sieht alle Daten
        }
    }
}
```

---

## Fix: JpaConfig.java

```java
@Configuration
public class JpaConfig {

    @Bean
    @Primary
    public DataSource tenantAwareDataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password
    ) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);          // nexo_app (kein Superuser!)
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        config.setConnectionTimeout(30_000);

        // Connection-Init: Schema setzen
        config.setConnectionInitSql("SET search_path = public");

        return new TenantAwareDataSource(config);
    }
}
```

---

## Fix: TenantContext.java (getTenantIdOrNull hinzufügen)

```java
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        UUID id = CURRENT_TENANT.get();
        if (id == null) throw new TenantNotSetException("No tenant in context");
        return id;
    }

    // Neu: null-safe Getter für DataSource-Proxy
    public static UUID getTenantIdOrNull() {
        return CURRENT_TENANT.get();
    }

    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

---

## Fix: application.yml — zwei DataSource-Profile

```yaml
# Applikations-User (RLS aktiv)
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/nexo_ontology
    username: ${DB_APP_USER:nexo_app}
    password: ${DB_APP_PASSWORD:nexo_app_secret}

# Flyway läuft als Admin (bypassed RLS)
  flyway:
    url: jdbc:postgresql://postgres:5432/nexo_ontology
    user: ${DB_ADMIN_USER:nexo_admin}
    password: ${DB_ADMIN_PASSWORD:nexo_admin_secret}
    locations: classpath:db/migration
```

---

## .env.example Ergänzung

```env
# App User (unterliegt RLS)
DB_APP_USER=nexo_app
DB_APP_PASSWORD=nexo_app_secret

# Admin User (für Flyway + Super-Admin APIs)
DB_ADMIN_USER=nexo_admin
DB_ADMIN_PASSWORD=nexo_admin_secret
```

---

## Test-Query (manuell in psql)

```sql
-- Als nexo_app einloggen
SET app.tenant_id = '550e8400-e29b-41d4-a716-446655440000';
SELECT COUNT(*) FROM objects;
-- → Nur Objects des gesetzten Tenants

SET app.tenant_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';
SELECT COUNT(*) FROM objects;
-- → Nur Objects dieses Tenants (andere Zahl)

-- Ohne Setting
RESET app.tenant_id;
SELECT COUNT(*) FROM objects;
-- → 0 Rows (RLS blockiert alles)
```

---

## Akzeptanzkriterien

- [ ] `nexo_app` User existiert und hat keine Superuser-Rechte
- [ ] `ENABLE ROW LEVEL SECURITY` aktiv auf allen 15 Tabellen
- [ ] Query ohne `app.tenant_id` Setting gibt 0 Rows zurück
- [ ] Query mit Tenant A gibt keine Daten von Tenant B zurück
- [ ] Flyway-Migrations laufen als Admin-User ohne RLS-Probleme durch
- [ ] Integration Test: zwei Tenants anlegen, Objects in beiden, Query zeigt Isolation
