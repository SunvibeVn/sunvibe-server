package vn.sunvibe.auth.core.domain.model.user.vo;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

/**
 * A Vietnamese phone number. {@link #create} is the one phone policy of the system (auth-email-access BR-21, BR-23):
 * {@link #key()} is the E.164 value accounts are compared by, {@link #national()} the form shown to people.
 * <p>
 * Only digits and the marks people write between them reach the parser, which would otherwise accept letters,
 * extensions and non-ASCII digits or convert them.
 */
public final class PhoneNumber extends ValueObject<PhoneNumber> {

    /** Longest input taken at all, before the marks are removed. */
    public static final int MAX_RAW_LENGTH = 32;

    private static final String COUNTRY_CODE = "84";
    private static final String REGION = "VN";
    private static final String TRUNK_PREFIX = "0";
    private static final String PRESENTATION_MARKS = " .-()";
    private static final PhoneNumberUtil PARSER = PhoneNumberUtil.getInstance();

    private final String key;
    private final String national;

    private PhoneNumber(String key, String national) {
        this.key = key;
        this.national = national;
    }

    /**
     * Builds the number from user input: {@code 0...}, {@code 84...} or {@code +84...}, with or without marks.
     *
     * @throws AuthValidationException if the input breaks the policy; the message never quotes it
     */
    public static PhoneNumber create(String raw) {
        if (raw == null || raw.length() > MAX_RAW_LENGTH) {
            throw invalid();
        }
        String nationalDigits = nationalDigits(withoutMarks(raw));
        com.google.i18n.phonenumbers.Phonenumber.PhoneNumber parsed;
        try {
            parsed = PARSER.parse("+" + COUNTRY_CODE + nationalDigits, null);
        } catch (NumberParseException e) {
            throw invalid();
        }
        if (!PARSER.isValidNumberForRegion(parsed, REGION)) {
            throw invalid();
        }
        return new PhoneNumber(PARSER.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
                TRUNK_PREFIX + nationalDigits);
    }

    /** The E.164 form, for example {@code +84901234567}. */
    public String key() {
        return key;
    }

    /** The national form, for example {@code 0901234567}. */
    public String national() {
        return national;
    }

    /** What remains must be {@code +?[0-9]+}: anything else, a control character included, is refused here. */
    private static String withoutMarks(String raw) {
        StringBuilder compact = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (PRESENTATION_MARKS.indexOf(c) >= 0) {
                continue;
            }
            boolean digit = c >= '0' && c <= '9';
            boolean leadingPlus = c == '+' && compact.isEmpty();
            if (!digit && !leadingPlus) {
                throw invalid();
            }
            compact.append(c);
        }
        return compact.toString();
    }

    /** No guessing: an international prefix, a bare national number or a 0 after the country code or the trunk are refused. */
    private static String nationalDigits(String compact) {
        String nationalDigits;
        if (compact.startsWith("+" + COUNTRY_CODE)) {
            nationalDigits = compact.substring(COUNTRY_CODE.length() + 1);
        } else if (compact.startsWith(COUNTRY_CODE)) {
            nationalDigits = compact.substring(COUNTRY_CODE.length());
        } else if (compact.startsWith(TRUNK_PREFIX)) {
            nationalDigits = compact.substring(TRUNK_PREFIX.length());
        } else {
            throw invalid();
        }
        // The parser would drop this 0, so 00901234567 would become the number of somebody else
        if (nationalDigits.startsWith(TRUNK_PREFIX)) {
            throw invalid();
        }
        return requirePresent(nationalDigits);
    }

    private static String requirePresent(String nationalDigits) {
        if (nationalDigits.isEmpty()) {
            throw invalid();
        }
        return nationalDigits;
    }

    private static AuthValidationException invalid() {
        return new AuthValidationException(AuthFieldName.USER_NAME, "Phone number is invalid.");
    }

    @Override
    protected boolean equalsCore(PhoneNumber other) {
        return key.equals(other.key);
    }

    @Override
    protected int hashCodeCore() {
        return key.hashCode();
    }

    /** Without the digits: a phone number is personal data. */
    @Override
    public String toString() {
        return "PhoneNumber[hidden]";
    }
}
