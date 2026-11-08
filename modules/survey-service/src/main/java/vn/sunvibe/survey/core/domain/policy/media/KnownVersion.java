package vn.sunvibe.survey.core.domain.policy.media;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The version of a media list a viewer last applied (survey-media spec A-30):
 * {@code <setVersion>.<identificationVersion>.<conditionVersion>.<checklistVersion>.<stateEpoch>.<viewDigest>}, five
 * non-negative integers and 8 hex characters of a SHA-256 of this viewer's editability, upload, intake, serving and
 * hold inputs. A list answers 204 only when every component is equal, so any input change gives a 200 at once.
 */
public record KnownVersion(long setVersion, long identificationVersion, long conditionVersion, long checklistVersion,
                           long stateEpoch, String viewDigest) {

    private static final Pattern FORMAT = Pattern.compile(
            "(\\d{1,18})\\.(\\d{1,18})\\.(\\d{1,18})\\.(\\d{1,18})\\.(\\d{1,18})\\.([0-9a-f]{8})");

    /** The parsed version, or empty when the text is malformed (400 field {@code knownVersion}). */
    public static Optional<KnownVersion> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new KnownVersion(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)),
                Long.parseLong(matcher.group(3)), Long.parseLong(matcher.group(4)), Long.parseLong(matcher.group(5)),
                matcher.group(6)));
    }

    public String format() {
        return setVersion + "." + identificationVersion + "." + conditionVersion + "." + checklistVersion + "."
                + stateEpoch + "." + viewDigest;
    }

    /**
     * survey-media spec A-30: the first 8 hex characters of a SHA-256 over this viewer's inputs.
     *
     * @param readOnlyReasonOrNull null when the checklist is editable for the viewer
     */
    public static String viewDigest(String readOnlyReasonOrNull, boolean uploadEnabled, boolean intakePaused,
                                    boolean servingEnabled, boolean canHold) {
        String inputs = "editable=" + (readOnlyReasonOrNull == null ? "" : readOnlyReasonOrNull)
                + ";upload=" + uploadEnabled + ";paused=" + intakePaused + ";serving=" + servingEnabled
                + ";hold=" + canHold;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(inputs.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
