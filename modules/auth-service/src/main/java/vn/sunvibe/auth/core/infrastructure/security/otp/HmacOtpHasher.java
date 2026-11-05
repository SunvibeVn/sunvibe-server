package vn.sunvibe.auth.core.infrastructure.security.otp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.shared.OtpHasher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/** {@link OtpHasher} as HMAC-SHA-256 in hex; a key that is not base64 of at least 32 bytes stops the startup. */
@Component
public class HmacOtpHasher implements OtpHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    public HmacOtpHasher(@Value("${app.auth.otp-hash-key}") String base64Key) {
        byte[] decoded = decode(base64Key);
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.auth.otp-hash-key must hold at least 32 bytes");
        }
        this.key = new SecretKeySpec(decoded, ALGORITHM);
    }

    private static byte[] decode(String base64Key) {
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.auth.otp-hash-key must be base64");
        }
    }

    @Override
    public String hash(long rowId, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal((rowId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is not available", e);
        }
    }
}
