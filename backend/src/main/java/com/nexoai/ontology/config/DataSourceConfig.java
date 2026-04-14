package com.nexoai.ontology.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Wraps the Spring-Boot-managed HikariCP DataSource with a TenantAwareDataSource
 * so that every connection borrow automatically runs `SET app.tenant_id = '<uuid>'`.
 *
 * This is the runtime enforcement for the RLS policies defined in V10/V24.
 * Without this wrapper, RLS policies would not receive the tenant context and
 * would return zero rows for all queries (because current_tenant_id() returns
 * the sentinel UUID 00000000-... that matches no real tenant).
 */
@Configuration
public class DataSourceConfig {

    /** Raw connection properties — username/password/url read from spring.datasource.* */
    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /** HikariCP pool — picks up spring.datasource.hikari.* and credentials. */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource hikariDataSource(DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /** Primary DataSource — wraps Hikari with tenant-aware SET on every connection. */
    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource hikari) {
        return new TenantAwareDataSource(hikari);
    }
}
