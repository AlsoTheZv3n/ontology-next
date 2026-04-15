package com.nexoai.ontology.adapters.in.rest;

import com.nexoai.ontology.core.crypto.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for the connector_oauth_clients table (V27).
 *
 * Without this controller, operators had to write raw SQL — including
 * encrypting client secrets out-of-band before INSERTing them. That's both
 * fragile and a security hazard (secrets in shell history). This controller
 * accepts plaintext on the wire (HTTPS-only) and runs encryption inside
 * the service so the secret never lands in a log line.
 *
 * Reads NEVER return the secret — not even encrypted — to follow the
 * "leaves no trace" principle. The caller can still update the secret by
 * sending a fresh value to PUT.
 */
@RestController
@RequestMapping("/api/admin/oauth-clients")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
@Slf4j
public class OAuthClientAdminController {

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;

    @GetMapping
    public List<Map<String, Object>> list() {
        try {
            return jdbc.queryForList("""
                    SELECT connector_id, client_id, auth_url, token_url, scopes,
                           redirect_uri, extra_params, created_at,
                           CASE WHEN client_secret_enc IS NOT NULL AND client_secret_enc <> ''
                                THEN true ELSE false END AS has_secret
                      FROM connector_oauth_clients
                     ORDER BY connector_id
                    """);
        } catch (Exception e) {
            log.debug("oauth-client list failed (table likely absent): {}", e.getMessage());
            return List.of();
        }
    }

    public record OAuthClientUpsertRequest(
            String clientId,
            String clientSecret,   // optional on update — null/blank leaves existing alone
            String authUrl,
            String tokenUrl,
            String scopes,
            String redirectUri
    ) {}

    @PutMapping("/{connectorId}")
    public ResponseEntity<Map<String, String>> upsert(
            @PathVariable String connectorId,
            @RequestBody OAuthClientUpsertRequest req) {

        if (req.clientId() == null || req.clientId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "clientId required"));
        }

        boolean exists;
        try {
            Boolean res = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM connector_oauth_clients WHERE connector_id = ?)",
                    Boolean.class, connectorId);
            exists = Boolean.TRUE.equals(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }

        boolean rotateSecret = req.clientSecret() != null && !req.clientSecret().isBlank();

        if (exists) {
            // UPDATE — touch the secret only if a new one was supplied.
            if (rotateSecret) {
                jdbc.update("""
                        UPDATE connector_oauth_clients
                           SET client_id = ?, client_secret_enc = ?, auth_url = ?,
                               token_url = ?, scopes = ?, redirect_uri = ?
                         WHERE connector_id = ?
                        """,
                        req.clientId(), crypto.encrypt(req.clientSecret()), req.authUrl(),
                        req.tokenUrl(), req.scopes(), req.redirectUri(), connectorId);
            } else {
                jdbc.update("""
                        UPDATE connector_oauth_clients
                           SET client_id = ?, auth_url = ?, token_url = ?,
                               scopes = ?, redirect_uri = ?
                         WHERE connector_id = ?
                        """,
                        req.clientId(), req.authUrl(), req.tokenUrl(),
                        req.scopes(), req.redirectUri(), connectorId);
            }
            return ResponseEntity.ok(Map.of("status", "updated", "connectorId", connectorId,
                    "rotatedSecret", String.valueOf(rotateSecret)));
        }

        // INSERT — secret is required for the first registration.
        if (!rotateSecret) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "clientSecret required when creating a new oauth client"));
        }
        jdbc.update("""
                INSERT INTO connector_oauth_clients
                  (connector_id, client_id, client_secret_enc, auth_url, token_url, scopes, redirect_uri)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                connectorId, req.clientId(), crypto.encrypt(req.clientSecret()),
                req.authUrl(), req.tokenUrl(), req.scopes(), req.redirectUri());

        return ResponseEntity.status(201).body(Map.of(
                "status", "created", "connectorId", connectorId));
    }

    @DeleteMapping("/{connectorId}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String connectorId) {
        int n = jdbc.update("DELETE FROM connector_oauth_clients WHERE connector_id = ?",
                connectorId);
        if (n == 0) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("status", "deleted", "connectorId", connectorId));
    }
}
