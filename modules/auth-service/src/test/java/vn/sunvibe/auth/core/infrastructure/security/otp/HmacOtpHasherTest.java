package vn.sunvibe.auth.core.infrastructure.security.otp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** auth-email-access PD-15, SV-096. */
class HmacOtpHasherTest {

    private static String key(String phrase) {
        return Base64.getEncoder().encodeToString(phrase.getBytes(StandardCharsets.UTF_8));
    }

    private final HmacOtpHasher hasher = new HmacOtpHasher(key("a-test-key-of-at-least-thirty-two-bytes"));

    @Test
    void hash_isHexAndHidesTheCode() {
        String hash = hasher.hash(1L, "123456");

        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertFalse(hash.contains("123456"));
    }

    @Test
    void hash_sameInput_sameResult() {
        assertEquals(hasher.hash(1L, "123456"), hasher.hash(1L, "123456"));
    }

    @Test
    void hash_sameCode_differsPerRow() {
        assertNotEquals(hasher.hash(1L, "123456"), hasher.hash(2L, "123456"));
    }

    /** Without the key a reader of the table cannot try the six-digit codes. */
    @Test
    void hash_differsPerKey() {
        HmacOtpHasher other = new HmacOtpHasher(key("another-test-key-of-at-least-thirty-two-bytes"));

        assertNotEquals(hasher.hash(1L, "123456"), other.hash(1L, "123456"));
    }

    @Test
    void create_shortKey_refused() {
        assertThrows(IllegalStateException.class, () -> new HmacOtpHasher(key("too-short")));
    }

    @Test
    void create_notBase64_refused() {
        assertThrows(IllegalStateException.class, () -> new HmacOtpHasher("***not base64***"));
    }

    @Test
    void create_failure_quotesNoKey() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> new HmacOtpHasher("***not base64***"));

        assertFalse(refused.getMessage().contains("***"));
    }
}
