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
import java.util.Base64;

/**
 * AES-256-GCM encryption for secrets that need to round-trip through the database
 * (OAuth client secrets, refresh tokens, per-tenant connector config values).
 *
 * Key lifecycle: a single master key is loaded from nexo.encryption.key (a
 * base64 of 32 random bytes, set at deploy time). Each encrypt() call generates
 * a fresh 12-byte IV and prepends it to the ciphertext before base64-encoding,
 * so the output is self-contained. Decryption inverts that layout.
 *
 * Key rotation is not implemented yet — when it's needed, prefix the ciphertext
 * with a single-byte version marker and keep a map of keys by version. The
 * current format reserves byte 0 as the implicit "version 0" by being IV.
 *
 * If nexo.encryption.key is missing OR not 32 bytes after base64 decode, the
 * bean fails to construct. That's intentional: starting the app with a broken
 * key silently is worse than failing fast — any persisted secrets would be
 * unrecoverable.
 */
@Service
@Slf4j
public class CryptoService {

    private static final int IV_LENGTH = 12;     // 96-bit IV recommended for GCM
    private static final int TAG_LENGTH = 128;   // bits
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretKey key;

    public CryptoService(@Value("${nexo.encryption.key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "nexo.encryption.key is required (base64-encoded 32-byte AES key)");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("nexo.encryption.key is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "nexo.encryption.key must decode to 32 bytes (AES-256), got " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Package-private constructor for tests — inject raw key bytes directly. */
    CryptoService(byte[] rawKey) {
        if (rawKey == null || rawKey.length != 32) {
            throw new IllegalArgumentException("rawKey must be 32 bytes");
        }
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            RNG.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
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
        try {
            byte[] raw = Base64.getDecoder().decode(b64);
            if (raw.length <= IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            System.arraycopy(raw, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decryption failed", e);
        }
    }
}
