package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

import java.util.Locale;

/**
 * An e-mail address. {@link #create} is the one email policy of the system (auth-email-access BR-20, BR-23), reused by
 * the DTO validators and the key backfill: its result is the canonical value that accounts are compared by.
 * {@link #from} rebuilds what is stored, unchanged.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Email extends ValueObject<Email> {

    /** Longest input taken at all, before the spaces around it are removed. */
    public static final int MAX_RAW_LENGTH = 320;
    public static final int MAX_LENGTH = 254;
    public static final int MAX_LOCAL_PART_LENGTH = 64;

    private static final int MAX_LABEL_LENGTH = 63;
    private static final String ATEXT_SYMBOLS = "!#$%&'*+/=?^_`{|}~-";

    private final String value;

    /**
     * Builds the canonical address from user input: spaces around it are removed, ASCII letters are lower-cased, dots
     * and plus signs stay.
     *
     * @throws AuthValidationException if the input breaks the policy; the message never quotes it
     */
    public static Email create(String raw) {
        if (raw == null || raw.length() > MAX_RAW_LENGTH || hasControlCharacter(raw)) {
            throw invalid();
        }
        String trimmed = stripSpaces(raw);
        if (trimmed.isEmpty() || !isPrintableAscii(trimmed)) {
            throw invalid();
        }
        String address = trimmed.toLowerCase(Locale.ROOT);
        int at = address.indexOf('@');
        if (at < 0 || at != address.lastIndexOf('@') || address.length() > MAX_LENGTH) {
            throw invalid();
        }
        String localPart = address.substring(0, at);
        if (localPart.isEmpty() || localPart.length() > MAX_LOCAL_PART_LENGTH || !isDotAtom(localPart)
                || !isDomain(address.substring(at + 1))) {
            throw invalid();
        }
        return new Email(address);
    }

    /** Rebuilds a stored value exactly as it is: a legacy row may hold a value {@link #create} would refuse or change. */
    public static Email from(String value) {
        return new Email(value);
    }

    private static AuthValidationException invalid() {
        return new AuthValidationException(AuthFieldName.EMAIL, "Invalid email format.");
    }

    private static boolean hasControlCharacter(String raw) {
        return raw.chars().anyMatch(c -> c < 0x20 || c == 0x7F);
    }

    /** U+0020 only: a tab or NBSP around an address is refused, not cleaned. */
    private static String stripSpaces(String raw) {
        int start = 0;
        int end = raw.length();
        while (start < end && raw.charAt(start) == ' ') {
            start++;
        }
        while (end > start && raw.charAt(end - 1) == ' ') {
            end--;
        }
        return raw.substring(start, end);
    }

    private static boolean isPrintableAscii(String value) {
        return value.chars().allMatch(c -> c <= 0x7E);
    }

    /** Dot-separated atoms of RFC 5322 atext: no quotes, and no leading, trailing or double dot. */
    private static boolean isDotAtom(String localPart) {
        for (String atom : localPart.split("\\.", -1)) {
            if (atom.isEmpty() || !atom.chars().allMatch(Email::isAtext)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAtext(int c) {
        return isLetterOrDigit(c) || ATEXT_SYMBOLS.indexOf(c) >= 0;
    }

    /** At least two labels; the last one has two characters or more and is not all digits, which refuses an IP. */
    private static boolean isDomain(String domain) {
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (!isLabel(label)) {
                return false;
            }
        }
        String top = labels[labels.length - 1];
        return top.length() >= 2 && !top.chars().allMatch(Email::isDigit);
    }

    private static boolean isLabel(String label) {
        if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH
                || label.charAt(0) == '-' || label.charAt(label.length() - 1) == '-') {
            return false;
        }
        return label.chars().allMatch(c -> isLetterOrDigit(c) || c == '-');
    }

    private static boolean isLetterOrDigit(int c) {
        return (c >= 'a' && c <= 'z') || isDigit(c);
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    @Override
    protected boolean equalsCore(Email other) {
        return this.value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }

}
