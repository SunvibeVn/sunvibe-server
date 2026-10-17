package vn.sunvibe.acquisition.core.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

class HmacConsentHasherTest {

    private static final byte[] KEY_BYTES = filled(32, (byte) 7);
    private static final String KEY = Base64.getEncoder().encodeToString(KEY_BYTES);

    @Test
    void hash_sameInput_isDeterministicAnd64Hex() {
        HmacConsentHasher hasher = new HmacConsentHasher(KEY);

        String first = hasher.hash("203.0.113.9");

        assertEquals(first, hasher.hash("203.0.113.9"));
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void hash_differentInput_differs() {
        HmacConsentHasher hasher = new HmacConsentHasher(KEY);

        assertNotEquals(hasher.hash("203.0.113.9"), hasher.hash("203.0.113.10"));
    }

    @Test
    void hash_differentKey_differs() {
        String otherKey = Base64.getEncoder().encodeToString(filled(32, (byte) 8));

        assertNotEquals(new HmacConsentHasher(KEY).hash("203.0.113.9"),
                new HmacConsentHasher(otherKey).hash("203.0.113.9"));
    }

    @Test
    void hash_knownVector() throws Exception {
        // Independent computation with javax.crypto.Mac over the same decoded key
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY_BYTES, "HmacSHA256"));
        String expected = HexFormat.of().formatHex(mac.doFinal("Mozilla/5.0 (Ơ)".getBytes(StandardCharsets.UTF_8)));

        assertEquals(expected, new HmacConsentHasher(KEY).hash("Mozilla/5.0 (Ơ)"));
    }

    @Test
    void hash_output_doesNotContainTheInput() {
        String input = "203.0.113.9";

        assertFalse(new HmacConsentHasher(KEY).hash(input).contains(input));
    }

    @Test
    void hash_null_returnsNull() {
        assertNull(new HmacConsentHasher(KEY).hash(null));
    }

    @Test
    void constructor_notBase64_throwsWithoutQuotingTheKey() {
        String notBase64 = "not-base64-secret-value!!";

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new HmacConsentHasher(notBase64));

        assertFalse(e.getMessage().contains(notBase64));
        assertTrue(e.getCause() == null || !String.valueOf(e.getCause().getMessage()).contains(notBase64));
    }

    @Test
    void constructor_31Bytes_throws() {
        String shortKey = Base64.getEncoder().encodeToString(filled(31, (byte) 7));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> new HmacConsentHasher(shortKey));

        assertFalse(e.getMessage().contains(shortKey));
    }

    @Test
    void constructor_32Bytes_ok() {
        assertDoesNotThrow(() -> new HmacConsentHasher(KEY));
    }

    @Test
    void constructor_nullKey_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> new HmacConsentHasher(null));
    }

    private static byte[] filled(int length, byte value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }
}
