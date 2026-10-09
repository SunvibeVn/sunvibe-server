package vn.sunvibe.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmCryptoServiceTest {

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private final CryptoService cryptoService =
            new AesGcmCryptoService(randomBase64Key(), randomBase64Key());

    @Test
    void encryptThenDecrypt_recoversOriginalPlaintext() {
        String plaintext = "123456789012";
        String ciphertext = cryptoService.encrypt(plaintext);
        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, cryptoService.decrypt(ciphertext));
    }

    @Test
    void encrypt_isNonDeterministic() {
        String plaintext = "123456789012";
        assertNotEquals(cryptoService.encrypt(plaintext), cryptoService.encrypt(plaintext));
    }

    @Test
    void blindIndex_isDeterministicForSameInput() {
        assertEquals(cryptoService.blindIndex("123456789012"), cryptoService.blindIndex("123456789012"));
    }

    @Test
    void blindIndex_differsForDifferentInput() {
        assertNotEquals(cryptoService.blindIndex("123456789012"), cryptoService.blindIndex("123456789013"));
    }

    @Test
    void blindIndex_isHexEncodedSha256HmacLength() {
        // HMAC-SHA256 output is 32 bytes -> 64 hex characters
        assertEquals(64, cryptoService.blindIndex("123456789012").length());
    }
}
