package com.nexoai.ontology;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full Spring application context against a real Postgres container.
 *
 * Gated behind a system property because Testcontainers needs a running Docker
 * daemon, which not every CI / local environment has. Run explicitly with:
 *
 *     mvn test -Dnexo.test.integration=true -Dtest=ApplicationContextIT
 *
 * What this catches that unit tests don't:
 *   - every @Service / @Component resolves its constructor args
 *   - all 27 Flyway migrations apply cleanly to a fresh DB
 *   - @Value bindings (NEXO_ENCRYPTION_KEY, JWT_SECRET, ...) all succeed
 *   - the filter chain (JwtAuthFilter + TenantInterceptor + RateLimitFilter)
 *     actually wires up without circular dependencies
 *
 * If this test is green, ~80% of "doesn't boot in prod" risks are gone.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "nexo.test.integration", matches = "true")
class ApplicationContextIT {

    @SuppressWarnings("resource")  // Testcontainers manages lifecycle
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("nexo_test")
            .withUsername("nexo")
            .withPassword("nexo");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Redis is wired @ConditionalOnProperty — leave unset to skip.
        registry.add("nexo.cdc.enabled", () -> "false");
        registry.add("nexo.encryption.key",
                () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("nexo.llm.provider", () -> "fallback");
        // Disable auth for this smoke test — the point is context load, not security rules.
        registry.add("nexo.security.auth-enforced", () -> "false");
    }

    @org.springframework.beans.factory.annotation.Autowired
    ApplicationContext context;

    @org.springframework.beans.factory.annotation.Autowired
    Flyway flyway;

    @Test
    void context_loads_with_all_beans() {
        assertThat(context).isNotNull();
        // Sanity bound — we currently ship ~150+ beans. If this drops drastically,
        // something is being conditionally excluded that probably shouldn't be.
        assertThat(context.getBeanDefinitionCount()).isGreaterThan(100);
    }

    @Test
    void all_flyway_migrations_applied_successfully() {
        MigrationInfo[] applied = flyway.info().applied();
        // V1..V27 at time of writing — update the lower bound if migrations grow.
        assertThat(applied).hasSizeGreaterThanOrEqualTo(27);
        for (MigrationInfo m : applied) {
            assertThat(m.getState().isApplied())
                    .as("migration %s state %s", m.getVersion(), m.getState())
                    .isTrue();
        }
    }
}
