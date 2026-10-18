package vn.sunvibe.auth.core.domain.repository.user;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * One (account, role) pair of a staff query: an account holding two of the asked roles gives two rows.
 * Carries only what a staff directory shows; no phone, e-mail or username.
 */
public record StaffAccountRow(String userId, String fullName, String roleName) {

    /**
     * Staff order everywhere a list is shown or cut: full name by Vietnamese collation (accents ignored,
     * primary strength), then user id, then role name. The repository cuts a list with the same order the
     * service sorts it with, so a cut keeps the same accounts.
     */
    public static Comparator<StaffAccountRow> nameOrder() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        collator.setStrength(Collator.PRIMARY);
        return Comparator.comparing(StaffAccountRow::fullName, collator)
                .thenComparing(StaffAccountRow::userId)
                .thenComparing(StaffAccountRow::roleName);
    }
}
