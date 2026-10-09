package vn.sunvibe.auth.core.application.shared;

/**
 * Partly hides a phone number or e-mail so a response can say where a code went
 * without disclosing the full contact.
 */
public final class ContactMasker {

    private ContactMasker() {
    }

    /** 0912345678 → 091****678 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "*".repeat(phone.length() - 6) + phone.substring(phone.length() - 3);
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
