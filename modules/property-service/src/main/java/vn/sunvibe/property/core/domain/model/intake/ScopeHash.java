package vn.sunvibe.property.core.domain.model.intake;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;

/** The hash of a selection's space ids (property-create intake I-3): a proof counts only for the scope it was given on. */
public final class ScopeHash {

    private ScopeHash() {
    }

    public static String of(Collection<String> spaceIds) {
        return sha256(String.join(",", spaceIds.stream().sorted().toList()));
    }

    /** SHA-256 in lower-case hex. */
    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM has SHA-256.", e);
        }
    }
}
