package vn.sunvibe.property.core.domain.policy.intake;

import vn.sunvibe.property.core.domain.model.intake.ScopeHash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * The confirmation code and the check phrase (property-create intake A-5, A-14): Crockford base32 from SecureRandom, stored
 * as SHA-256 salted by the row's id and compared in constant time. A code alone gives nothing, so the keyed hash of a one-time code is not
 * needed here. Read out by phone, so case does not count, O reads as 0, and I and L as 1.
 */
public final class IntakeCodes {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int PHRASE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private IntakeCodes() {
    }

    public static String newCode() {
        return random(CODE_LENGTH);
    }

    public static String newPhrase() {
        return random(PHRASE_LENGTH);
    }

    public static String hash(String rowId, String value) {
        return ScopeHash.sha256(rowId + ":" + fold(value));
    }

    public static boolean matches(String rowId, String value, String storedHash) {
        if (value == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(rowId, value).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String fold(String value) {
        return value.strip().toUpperCase(Locale.ROOT).replace('O', '0').replace('I', '1').replace('L', '1');
    }

    private static String random(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return text.toString();
    }
}
