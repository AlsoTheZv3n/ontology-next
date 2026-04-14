package com.nexoai.ontology.core.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexoai.ontology.core.crypto.CryptoService;
import com.nexoai.ontology.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Three-step OAuth2 authorization-code flow for external connectors
 * (HubSpot, Salesforce, GitHub, ...).
 *
 *   1. buildAuthorizationUrl — generate a random state, persist it in
 *      connector_oauth_states with a TTL, and return the provider's
 *      authorize URL with client_id + redirect_uri + scope + state.
 *   2. exchangeCode — called from the callback; validates state freshness
 *      and one-time use, POSTs to the provider's token endpoint, stores
 *      the access + refresh tokens encrypted via CryptoService.
 *   3. getValidAccessToken / refresh — out of scope here; see scheduler.
 *
 * Token HTTP calls go through the JDK HttpClient so this class is easy to
 * exercise in unit tests — swap the client for one that targets a local
 * stub server. No Spring WebFlux / WebClient dependency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2ConnectorService {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    @Value("${nexo.base-url:http://localhost:8081}")
    private String baseUrl;

    public record AuthorizationUrl(String url, String state) {}

    /**
     * Build and return the URL to which the UI should redirect the user. A
     * new state token is persisted so the callback can prove authenticity.
     */
    @Transactional
    public AuthorizationUrl buildAuthorizationUrl(String connectorId, UUID userId) {
        UUID tenantId = TenantContext.getTenantId();
        Map<String, Object> client = jdbc.queryForMap(
                "SELECT client_id, auth_url, scopes FROM connector_oauth_clients WHERE connector_id = ?",
                connectorId);

        String state = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO connector_oauth_states (state, tenant_id, user_id, connector_id)
                VALUES (?, ?::uuid, ?::uuid, ?)
                """,
                state, tenantId.toString(),
                userId == null ? null : userId.toString(), connectorId);

        String redirectUri = baseUrl + "/api/v1/connectors/oauth/callback";
        String url = String.valueOf(client.get("auth_url"))
                + "?response_type=code"
                + "&client_id=" + enc(String.valueOf(client.get("client_id")))
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(String.valueOf(client.get("scopes")))
                + "&state=" + state;
        return new AuthorizationUrl(url, state);
    }

    public record ExchangeResult(UUID dataSourceId, String scope, Instant expiresAt) {}

    /**
     * Exchange the authorization code for tokens. Fails hard on state replay
     * or TTL expiry so a leaked state can't be used twice.
     */
    @Transactional
    public ExchangeResult exchangeCode(String state, String code) {
        if (state == null || state.isBlank() || code == null || code.isBlank()) {
            throw new IllegalArgumentException("state and code are required");
        }

        Map<String, Object> stateRow = jdbc.queryForMap("""
                SELECT tenant_id, user_id, connector_id, consumed_at, created_at
                  FROM connector_oauth_states
                 WHERE state = ?
                """, state);
        if (stateRow.get("consumed_at") != null) {
            throw new IllegalStateException("state already consumed (replay)");
        }
        Instant createdAt = toInstant(stateRow.get("created_at"));
        if (createdAt != null && createdAt.isBefore(Instant.now().minus(STATE_TTL))) {
            throw new IllegalStateException("state expired");
        }

        String connectorId = String.valueOf(stateRow.get("connector_id"));
        Map<String, Object> client = jdbc.queryForMap("""
                SELECT client_id, client_secret_enc, token_url
                  FROM connector_oauth_clients
                 WHERE connector_id = ?
                """, connectorId);
        String clientSecret = crypto.decrypt(String.valueOf(client.get("client_secret_enc")));
        String redirectUri = baseUrl + "/api/v1/connectors/oauth/callback";

        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri)
                + "&client_id=" + enc(String.valueOf(client.get("client_id")))
                + "&client_secret=" + enc(clientSecret);

        JsonNode tokenResponse = postForm(String.valueOf(client.get("token_url")), form);
        String accessToken = tokenResponse.path("access_token").asText("");
        if (accessToken.isEmpty()) {
            throw new IllegalStateException(
                    "token endpoint returned no access_token: " + tokenResponse);
        }
        String refreshToken = tokenResponse.path("refresh_token").asText(null);
        long expiresIn = tokenResponse.path("expires_in").asLong(3600);
        Instant expiresAt = Instant.now().plusSeconds(expiresIn);

        ObjectNode config = mapper.createObjectNode();
        config.put("access_token_enc", crypto.encrypt(accessToken));
        if (refreshToken != null) config.put("refresh_token_enc", crypto.encrypt(refreshToken));
        config.put("expires_at", expiresAt.toString());
        String scope = tokenResponse.path("scope").asText("");
        config.put("scope", scope);

        UUID dsId = UUID.randomUUID();
        UUID tenantId = UUID.fromString(stateRow.get("tenant_id").toString());
        jdbc.update("""
                INSERT INTO data_source_definitions
                  (id, tenant_id, api_name, display_name, connector_type, config)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?::jsonb)
                """,
                dsId.toString(), tenantId.toString(),
                connectorId + "-" + dsId.toString().substring(0, 8),
                connectorId,
                connectorId,
                config.toString());

        jdbc.update("UPDATE connector_oauth_states SET consumed_at = NOW() WHERE state = ?", state);

        return new ExchangeResult(dsId, scope, expiresAt);
    }

    private JsonNode postForm(String url, String form) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalStateException(
                        "token endpoint returned HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            throw new IllegalStateException("token exchange failed: " + e.getMessage(), e);
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private static Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp ts) return ts.toInstant();
        if (o instanceof Instant in) return in;
        try { return Instant.parse(o.toString()); } catch (Exception e) { return null; }
    }
}
