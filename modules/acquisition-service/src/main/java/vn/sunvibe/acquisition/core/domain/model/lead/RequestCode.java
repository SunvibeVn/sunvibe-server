package vn.sunvibe.acquisition.core.domain.model.lead;

import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.acquisition.core.domain.model.ValueObject;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Public code of an owner lead, shown to the owner after sending the form: {@code SV-XXXX-XXXX}
 * (acquisition-owner-intake BR-11). The alphabet leaves out 0, 1, I and O so a code read aloud or copied by hand is
 * not mistaken.
 */
public final class RequestCode extends ValueObject<RequestCode> {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Pattern FORMAT = Pattern.compile("^SV-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$");

    private final String value;

    private RequestCode(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidIntakeValueException("requestCode", "Request code does not have the format SV-XXXX-XXXX.");
        }
        this.value = value;
    }

    /**
     * Draw a new code from the given random source. The caller checks that the code is still free.
     */
    public static RequestCode generate(SecureRandom random) {
        StringBuilder code = new StringBuilder("SV-");
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new RequestCode(code.toString());
    }

    /**
     * Rebuild a code from its stored text; refuses a text that is not a valid code.
     */
    public static RequestCode of(String value) {
        return new RequestCode(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    protected boolean equalsCore(RequestCode other) {
        return other != null && value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
