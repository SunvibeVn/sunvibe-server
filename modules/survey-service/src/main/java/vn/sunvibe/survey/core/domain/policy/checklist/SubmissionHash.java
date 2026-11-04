package vn.sunvibe.survey.core.domain.policy.checklist;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * {@code contentSha256} of a submission (survey-checklist submission spec 4.3, plan PD-1): the SHA-256 of the canonical
 * JSON of the group contents and the evidence list, as UTF-8. A test recomputes it; no runtime check in this slice.
 */
public final class SubmissionHash {

    private SubmissionHash() {
    }

    /**
     * @param canonicalJson the canonical form (keys sorted), from {@code SubmissionCanonicalForm}
     */
    public static String of(String canonicalJson) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java platform.", e);
        }
    }
}
