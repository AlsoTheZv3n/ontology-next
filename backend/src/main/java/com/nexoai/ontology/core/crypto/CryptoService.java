package com.nexoai.ontology.core.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AES-256-GCM encryption for secrets that need to round-trip through the database
 * (OAuth client secrets, refresh tokens, per-tenant connector config values).
 *
 * Key rotation (gap-15):
 *   - {@code nexo.encryption.key} is the ACTIVE key — used for every encrypt() call.
 *   - {@code nexo.encryption.key.legacy} is OPTIONAL and accepts a comma-separated
 *     list of older keys. decrypt() tries the active key first, then each legacy key
 *     in turn. As long as the legacy key list still contains the key that wrote a
 *     given ciphertext, that ciphertext stays readable.
 *
 * Rotation flow at the operator level:
 *   1. Generate new key, set NEXO_ENCRYPTION_KEY=<new>.
 *   2. Move the old key to NEXO_ENCRYPTION_KEY_LEGACY=<old>.
 *   3. Restart — both keys can now decrypt; new writes use the active key.
 *   4. Re-encrypt persisted secrets at leisure (admin job — out of scope here,
 *      tracked in gap-15 todo as a follow-up).
 *   5. Once everything is re-encrypted, drop the legacy env var and restart.
 *
 * If nexo.encryption.key is missing OR not 32 bytes after base64 decode, the
 * bean fails to construct. Failing fast at boot is intentional — silently running
 * with a broken key would produce ciphertexts no one can decrypt later.
 */
@Service
@Slf4j
public class CryptoService {

    private static final int IV_LENGTH = 12;     // 96-bit IV recommended for GCM
    private static final int TAG_LENGTH = 128;   // bits
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretKey activeKey;
    private final List<SecretKey> legacyKeys;

    public CryptoService(@Value("${nexo.encryption.key:}") String activeBase64,
                          @Value("${nexo.encryption.key.legacy:}") String legacyCsv) {
        this.activeKey = parseKey(activeBase64, "nexo.encryption.key");
        this.legacyKeys = new ArrayList<>();
        if (legacyCsv != null && !legacyCsv.isBlank()) {
            for (String entry : legacyCsv.split(",")) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    legacyKeys.add(parseKey(trimmed, "nexo.encryption.key.legacy[]"));
                } catch (Exception e) {
                    log.warn("Skipping invalid legacy key entry: {}", e.getMessage());
                }
            }
            if (!legacyKeys.isEmpty()) {
                log.info("CryptoService loaded {} legacy key(s) for fallback decryption",
                        legacyKeys.size());
            }
        }
    }

    /** Package-private test constructor — inject raw key bytes directly, no legacy keys. */
    CryptoService(byte[] rawKey) {
        if (rawKey == null || rawKey.length != 32) {
            throw new IllegalArgumentException("rawKey must be 32 bytes");
        }
        this.activeKey = new SecretKeySpec(rawKey, "AES");
        this.legacyKeys = List.of();
    }

    /** Package-private test constructor — inject active + legacy keys for rotation tests. */
    CryptoService(byte[] activeRawKey, List<byte[]> legacyRawKeys) {
        if (activeRawKey == null || activeRawKey.length != 32) {
            throw new IllegalArgumentException("activeRawKey must be 32 bytes");
        }
        this.activeKey = new SecretKeySpec(activeRawKey, "AES");
        List<SecretKey> ls = new ArrayList<>();
        for (byte[] k : legacyRawKeys) {
            if (k == null || k.length != 32) continue;
            ls.add(new SecretKeySpec(k, "AES"));
        }
        this.legacyKeys = ls;
    }

    private static SecretKey parseKey(String base64, String name) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(
                    name + " is required (base64-encoded 32-byte AES key)");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    name + " must decode to 32 bytes (AES-256), got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, activeKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    public String decrypt(String b64) {
        if (b64 == null) return null;
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("decryption failed: not base64", e);
        }
        if (raw.length <= IV_LENGTH) {
            throw new IllegalStateException("decryption failed: ciphertext too short");
        }
        // Try the active key first (the common case after rotation completes).
        try {
            return decryptWith(activeKey, raw);
        } catch (Exception primaryFailure) {
            for (SecretKey legacy : legacyKeys) {
                try {
                    return decryptWith(legacy, raw);
                } catch (Exception ignored) { /* try next */ }
            }
            throw new IllegalStateException(
                    "decryption failed with all configured keys", primaryFailure);
        }
    }

    private String decryptWith(SecretKey k, byte[] raw) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        byte[] ct = new byte[raw.length - IV_LENGTH];
        System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
        System.arraycopy(raw, IV_LENGTH, ct, 0, ct.length);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG_LENGTH, iv));
        return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
    }
}
