package vn.sunvibe.acquisition.core.domain.model.lead;

import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.acquisition.core.domain.model.ValueObject;

import java.util.regex.Pattern;

/**
 * Vietnamese mobile or landline number in one stored form: {@code +84} followed by 9 digits
 * (acquisition-owner-intake BR-2). The same number typed in different ways is therefore the same phone.
 */
public final class PhoneNumber extends ValueObject<PhoneNumber> {

    // Spaces and the separators . - ( ) are ignored; after that a national 0 or +84 prefix, then 9 digits.
    // 84 without the plus sign is refused on purpose: it is easy to confuse with a typing slip.
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");
    private static final Pattern FORMAT = Pattern.compile("^(0|\\+84)[1-9][0-9]{8}$");

    private final String value;

    private PhoneNumber(String value) {
        this.value = value;
    }

    /**
     * Strip the separators, check the format and return the number as {@code +84} plus 9 digits.
     */
    public static PhoneNumber parse(String raw) {
        String stripped = raw == null ? "" : SEPARATORS.matcher(raw).replaceAll("");
        if (!FORMAT.matcher(stripped).matches()) {
            throw new InvalidIntakeValueException("phone", "Phone number is not a valid Vietnamese phone number.");
        }
        String nineDigits = stripped.startsWith("+84") ? stripped.substring(3) : stripped.substring(1);
        return new PhoneNumber("+84" + nineDigits);
    }

    public String getValue() {
        return value;
    }

    @Override
    protected boolean equalsCore(PhoneNumber other) {
        return other != null && value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }

    /** The phone is personal data: never print it. */
    @Override
    public String toString() {
        return "PhoneNumber[hidden]";
    }
}
