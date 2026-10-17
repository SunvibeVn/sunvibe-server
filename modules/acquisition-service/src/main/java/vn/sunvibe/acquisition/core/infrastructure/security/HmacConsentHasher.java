package vn.sunvibe.acquisition.core.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.shared.external.security.ConsentHasher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC-SHA256 implementation of {@link ConsentHasher}. The key comes from the environment (base64, at least 32
 * bytes); a missing or weak key stops the application at startup. No message of this class ever contains the key.
 */
@Component
public class HmacConsentHasher implements ConsentHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final byte[] key;

    public HmacConsentHasher(@Value("${app.acquisition.hash-key}") String base64Key) {
        this.key = decode(base64Key);
    }

    @Override
    public String hash(String value) {
        if (value == null) {
            return null;
        }
        try {
            // Mac is not thread-safe, so each call builds its own
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", e);
        }
    }

    private static byte[] decode(String base64Key) {
        if (base64Key == null) {
            throw new IllegalStateException("The acquisition hash key is not set.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            // The decoder message can quote part of the key, so it is not chained
            throw new IllegalStateException("The acquisition hash key must be base64.");
        }
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("The acquisition hash key must decode to at least 32 bytes.");
        }
        return decoded;
    }
}
