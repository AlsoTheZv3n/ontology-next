package com.nexoai.ontology.core.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    private static byte[] randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return k;
    }

    private static CryptoService withKey() { return new CryptoService(randomKey()); }

    @Test
    void roundtrip_preserves_the_original_string() {
        CryptoService svc = withKey();
        String plain = "hunter2-very-secret-token";
        String ct = svc.encrypt(plain);
        assertThat(svc.decrypt(ct)).isEqualTo(plain);
    }

    @Test
    void ciphertext_differs_between_encryptions_of_the_same_plaintext() {
        CryptoService svc = withKey();
        String ct1 = svc.encrypt("same");
        String ct2 = svc.encrypt("same");
        // Fresh IV every encrypt → same plaintext must not produce the same ciphertext.
        assertThat(ct1).isNotEqualTo(ct2);
    }

    @Test
    void ciphertext_is_base64_and_longer_than_the_plaintext() {
        CryptoService svc = withKey();
        String ct = svc.encrypt("hi");
        // Must be valid base64 (throws if not).
        byte[] raw = Base64.getDecoder().decode(ct);
        // IV (12) + plaintext (2) + auth tag (16) = 30 bytes
        assertThat(raw.length).isEqualTo(30);
    }

    @Test
    void decrypt_with_wrong_key_fails() {
        CryptoService a = withKey();
        CryptoService b = withKey();
        String ct = a.encrypt("secret");
        assertThatThrownBy(() -> b.decrypt(ct))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test
    void null_input_returns_null() {
        CryptoService svc = withKey();
        assertThat(svc.encrypt(null)).isNull();
        assertThat(svc.decrypt(null)).isNull();
    }

    @Test
    void missing_key_fails_fast() {
        assertThatThrownBy(() -> new CryptoService("", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> new CryptoService((String) null, ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalid_base64_fails_fast() {
        assertThatThrownBy(() -> new CryptoService("not base64 !!!", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrong_length_key_fails_fast() {
        // 16 bytes is AES-128, not AES-256 — reject.
        String b64 = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new CryptoService(b64, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void legacy_key_can_decrypt_what_old_active_key_wrote() {
        byte[] oldRaw = randomKey();
        byte[] newRaw = randomKey();

        // Operator's pre-rotation deploy: encrypted with oldRaw.
        CryptoService oldSvc = new CryptoService(oldRaw);
        String ct = oldSvc.encrypt("oauth-secret");

        // Post-rotation: newRaw is active, oldRaw is in the legacy pool.
        CryptoService rotated = new CryptoService(newRaw, java.util.List.of(oldRaw));
        assertThat(rotated.decrypt(ct)).isEqualTo("oauth-secret");

        // New writes use the active key — and rotated can read its own writes too.
        String fresh = rotated.encrypt("new-secret");
        assertThat(rotated.decrypt(fresh)).isEqualTo("new-secret");
    }

    @Test
    void decrypt_fails_when_neither_active_nor_any_legacy_key_works() {
        byte[] writerKey = randomKey();
        byte[] activeKey = randomKey();
        byte[] otherLegacyKey = randomKey();

        String ct = new CryptoService(writerKey).encrypt("secret");

        // Active and legacy don't include writerKey.
        CryptoService svc = new CryptoService(activeKey, java.util.List.of(otherLegacyKey));
        assertThatThrownBy(() -> svc.decrypt(ct))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("all configured keys");
    }

    @Test
    void tampered_ciphertext_fails_auth() {
        CryptoService svc = withKey();
        String ct = svc.encrypt("payload");
        byte[] raw = Base64.getDecoder().decode(ct);
        raw[raw.length - 1] ^= 1; // flip one bit in the auth tag
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> svc.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
