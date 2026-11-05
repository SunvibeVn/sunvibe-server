package vn.sunvibe.auth.core.application.shared;

import java.util.regex.Pattern;

/**
 * Partly hides a phone number or e-mail so a response or an audit subject can say which contact is meant without
 * disclosing it. Masking a masked value changes nothing.
 */
public final class ContactMasker {

    /** The width of {@code auth_audit_events.subject}. */
    private static final int MAX_LENGTH = 100;
    private static final int MAX_STARS = 8;
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private ContactMasker() {
    }

    /**
     * The form for a value of unknown kind, such as what a client typed at sign-in: it may be long and hold control
     * characters, which PostgreSQL may refuse to store.
     *
     * @return null for null
     */
    public static String mask(String identifier) {
        if (identifier == null) {
            return null;
        }
        String clean = CONTROL.matcher(identifier).replaceAll("");
        String masked = clean.indexOf('@') >= 0 ? maskEmail(clean) : maskPhone(clean);
        return masked.length() > MAX_LENGTH ? masked.substring(0, MAX_LENGTH) : masked;
    }

    /** 0912345678 → 091****678 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "*".repeat(Math.min(phone.length() - 6, MAX_STARS)) + phone.substring(phone.length() - 3);
    }

    /** an.nguyen@example.com → a***@example.com */
    public static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
