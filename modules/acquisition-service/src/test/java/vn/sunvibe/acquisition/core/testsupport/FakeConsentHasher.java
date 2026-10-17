package vn.sunvibe.acquisition.core.testsupport;

import vn.sunvibe.acquisition.core.application.shared.external.security.ConsentHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Fake hasher: an unkeyed SHA-256 in lower-case hex, so equal values give equal 64-character hashes. */
public class FakeConsentHasher implements ConsentHasher {

    @Override
    public String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
