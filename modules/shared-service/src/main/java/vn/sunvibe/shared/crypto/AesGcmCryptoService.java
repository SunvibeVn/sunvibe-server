package vn.sunvibe.shared.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM field encryption plus an HMAC-SHA256 blind index.
 *
 * <p>Two independent keys are used — one for encryption, one for the blind
 * index — so that a compromise of one key doesn't also compromise the
 * other property (confidentiality vs. searchability).
 */
@Component
public class AesGcmCryptoService implements CryptoService {

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec indexKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCryptoService(
            @Value("${app.crypto.id-card-key}") String base64EncryptionKey,
            @Value("${app.crypto.id-card-index-key}") String base64IndexKey) {
        byte[] encryptionKeyBytes = decodeKey(base64EncryptionKey, "app.crypto.id-card-key");
        byte[] indexKeyBytes = decodeKey(base64IndexKey, "app.crypto.id-card-index-key");
        if (Arrays.equals(encryptionKeyBytes, indexKeyBytes)) {
            throw new IllegalArgumentException(
                    "app.crypto.id-card-key and app.crypto.id-card-index-key must be different keys");
        }
        this.encryptionKey = new SecretKeySpec(encryptionKeyBytes, "AES");
        this.indexKey = new SecretKeySpec(indexKeyBytes, "HmacSHA256");
    }

    private static byte[] decodeKey(String base64Key, String propertyName) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(propertyName + " must be valid base64", e);
        }
        if (decoded.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    propertyName + " must decode to exactly " + KEY_LENGTH_BYTES + " bytes");
        }
        return decoded;
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCiphertext = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
            System.arraycopy(encrypted, 0, ivAndCiphertext, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(ivAndCiphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] ivAndCiphertext = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] encrypted = new byte[ivAndCiphertext.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, iv.length);
            System.arraycopy(ivAndCiphertext, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }

    @Override
    public String blindIndex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(indexKey);
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute blind index", e);
        }
    }
}
