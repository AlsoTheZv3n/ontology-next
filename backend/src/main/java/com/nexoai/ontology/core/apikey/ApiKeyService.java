package com.nexoai.ontology.core.apikey;

import com.nexoai.ontology.core.exception.OntologyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final JdbcTemplate jdbcTemplate;

    private static final String KEY_PREFIX = "nxo_";
    private static final int KEY_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public Map<String, Object> createKey(UUID tenantId, String name, String scopes, OffsetDateTime expiresAt) {
        String randomPart = generateRandomString(KEY_LENGTH);
        String rawKey = KEY_PREFIX + randomPart;
        String keyPrefix = rawKey.substring(0, 12);
        String keyHash = sha256(rawKey);

        UUID keyId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO api_keys (id, tenant_id, key_prefix, key_hash, name, scopes, expires_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?::jsonb, ?)
                """,
                keyId.toString(), tenantId.toString(), keyPrefix, keyHash, name,
                scopes != null ? scopes : "[\"read:objects\"]",
                expiresAt != null ? expiresAt.toString() : null);

        log.info("API key created: prefix={} for tenant {}", keyPrefix, tenantId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", keyId);
        result.put("key", rawKey);
        result.put("keyPrefix", keyPrefix);
        result.put("name", name);
        result.put("createdAt", OffsetDateTime.now().toString());
        return result;
    }

    public Map<String, Object> validateKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return null;
        }

        String keyHash = sha256(rawKey);
        String keyPrefix = rawKey.substring(0, Math.min(12, rawKey.length()));

        try {
            Map<String, Object> keyRow = jdbcTemplate.queryForMap(
                    """
                    SELECT * FROM api_keys
                    WHERE key_prefix = ? AND key_hash = ? AND is_active = TRUE
                    """,
                    keyPrefix, keyHash);

            // Check expiry
            Object expiresAt = keyRow.get("expires_at");
            if (expiresAt != null) {
                OffsetDateTime expiry = OffsetDateTime.parse(expiresAt.toString());
                if (expiry.isBefore(OffsetDateTime.now())) {
                    return null;
                }
            }

            // Update last_used_at
            jdbcTemplate.update(
                    "UPDATE api_keys SET last_used_at = NOW() WHERE id = ?::uuid",
                    keyRow.get("id").toString());

            return keyRow;
        } catch (Exception e) {
            return null;
        }
    }

    public List<Map<String, Object>> listKeys(UUID tenantId) {
        return jdbcTemplate.queryForList(
                """
                SELECT id, tenant_id, key_prefix, name, scopes, expires_at, last_used_at, is_active, created_at
                FROM api_keys
                WHERE tenant_id = ?::uuid
                ORDER BY created_at DESC
                """,
                tenantId.toString());
    }

    public void revokeKey(UUID keyId) {
        int updated = jdbcTemplate.update(
                "UPDATE api_keys SET is_active = FALSE WHERE id = ?::uuid",
                keyId.toString());
        if (updated == 0) {
            throw new OntologyException("API key not found: " + keyId);
        }
        log.info("API key revoked: {}", keyId);
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(SECURE_RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new OntologyException("SHA-256 hashing failed");
        }
    }
}
