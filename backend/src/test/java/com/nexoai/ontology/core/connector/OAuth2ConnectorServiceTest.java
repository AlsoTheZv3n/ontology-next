package com.nexoai.ontology.core.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexoai.ontology.core.crypto.CryptoService;
import com.nexoai.ontology.core.tenant.TenantContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the OAuth2 flow end to end:
 *   buildAuthorizationUrl → exchangeCode (replay / expiry guards included).
 * A local HttpServer stands in for the provider's token endpoint so the test
 * really does POST a form and parses a JSON response — not a WebClient mock.
 */
class OAuth2ConnectorServiceTest {

    private JdbcTemplate jdbc;
    private CryptoService crypto;
    private ObjectMapper mapper;
    private OAuth2ConnectorService service;

    private HttpServer tokenServer;
    private String tokenUrl;
    private final AtomicReference<String> lastFormBody = new AtomicReference<>();
    private final AtomicReference<String> nextResponse =
            new AtomicReference<>("{\"access_token\":\"AT\",\"refresh_token\":\"RT\",\"expires_in\":3600,\"scope\":\"crm.read\"}");

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc = mock(JdbcTemplate.class);
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        crypto = new CryptoService(java.util.Base64.getEncoder().encodeToString(key), "");
        mapper = new ObjectMapper();
        service = new OAuth2ConnectorService(jdbc, crypto, mapper, HttpClient.newHttpClient());
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8081");

        tokenServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tokenServer.createContext("/token", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastFormBody.set(new String(body, StandardCharsets.UTF_8));
            byte[] resp = nextResponse.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        tokenServer.start();
        tokenUrl = "http://127.0.0.1:" + tokenServer.getAddress().getPort() + "/token";

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void cleanup() {
        tokenServer.stop(0);
        TenantContext.clear();
    }

    private void stubClientRow(String connectorId, String authUrl, String scopes) {
        when(jdbc.queryForMap(contains("FROM connector_oauth_clients"), eq(connectorId)))
                .thenReturn(Map.of(
                        "client_id", "cid-1",
                        "client_secret_enc", crypto.encrypt("s3cret"),
                        "auth_url", authUrl,
                        "token_url", tokenUrl,
                        "scopes", scopes
                ));
    }

    private void stubStateRow(String state, Instant createdAt, Timestamp consumedAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("tenant_id", tenantId.toString());
        row.put("user_id", userId.toString());
        row.put("connector_id", "hubspot");
        row.put("consumed_at", consumedAt);
        row.put("created_at", Timestamp.from(createdAt));
        when(jdbc.queryForMap(contains("FROM connector_oauth_states"), eq(state)))
                .thenReturn(row);
    }

    // ---------------------------------------------------------------

    @Test
    void buildAuthorizationUrl_returns_url_with_state_scope_and_redirect_uri() {
        stubClientRow("hubspot",
                "https://app.hubspot.com/oauth/authorize",
                "crm.objects.contacts.read oauth");

        var result = service.buildAuthorizationUrl("hubspot", userId);

        assertThat(result.url()).startsWith("https://app.hubspot.com/oauth/authorize?");
        assertThat(result.url()).contains("client_id=cid-1");
        assertThat(result.url()).contains("state=" + result.state());
        assertThat(result.url()).contains("scope=crm.objects.contacts.read+oauth");
        assertThat(result.url()).contains("redirect_uri=http%3A%2F%2Flocalhost%3A8081");
    }

    @Test
    void exchangeCode_posts_form_and_persists_encrypted_tokens() throws Exception {
        String state = "fresh-state";
        stubStateRow(state, Instant.now(), null);
        stubClientRow("hubspot", "https://ignored", "crm.read");

        var result = service.exchangeCode(state, "the-code");

        assertThat(result.dataSourceId()).isNotNull();
        assertThat(result.scope()).isEqualTo("crm.read");
        assertThat(result.expiresAt()).isAfter(Instant.now().plusSeconds(3500));
        // Form body really was POSTed.
        assertThat(lastFormBody.get())
                .contains("grant_type=authorization_code")
                .contains("code=the-code")
                .contains("client_id=cid-1")
                .contains("client_secret=s3cret");
        // INSERT was issued with encrypted tokens — not plaintext.
        verify(jdbc).update(contains("INSERT INTO data_source_definitions"),
                anyString(), anyString(), anyString(), anyString(), anyString(), argThat((String s) ->
                        s.contains("access_token_enc")
                                && !s.contains("\"access_token\":\"AT\"")
                                && s.contains("expires_at")));
        verify(jdbc).update(contains("UPDATE connector_oauth_states"), eq(state));
    }

    @Test
    void exchangeCode_rejects_replayed_state() {
        String state = "used-state";
        stubStateRow(state, Instant.now(), Timestamp.from(Instant.now()));
        stubClientRow("hubspot", "x", "y");

        assertThatThrownBy(() -> service.exchangeCode(state, "c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replay");
    }

    @Test
    void exchangeCode_rejects_expired_state() {
        String state = "old";
        stubStateRow(state, Instant.now().minusSeconds(3600), null);
        stubClientRow("hubspot", "x", "y");

        assertThatThrownBy(() -> service.exchangeCode(state, "c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void exchangeCode_requires_both_state_and_code() {
        assertThatThrownBy(() -> service.exchangeCode(null, "c"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.exchangeCode("s", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exchangeCode_fails_if_provider_returns_no_access_token() {
        String state = "ok";
        stubStateRow(state, Instant.now(), null);
        stubClientRow("hubspot", "x", "y");
        nextResponse.set("{\"error\":\"invalid_grant\"}");

        assertThatThrownBy(() -> service.exchangeCode(state, "c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no access_token");
    }
}
