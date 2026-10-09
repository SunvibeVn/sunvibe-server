package vn.sunvibe.auth.core.presentation.validation.user.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.StrongPassword;

import java.util.regex.Pattern;

/**
 * Checks {@link StrongPassword}: 8 to 72 characters with at least one
 * uppercase letter, one lowercase letter, one digit and one of {@code @$!%*?&},
 * and nothing else.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;

    /**
     * BCrypt only uses the first 72 bytes and Spring Security refuses to hash
     * longer input. The pattern only allows ASCII, so characters equal bytes.
     */
    private static final int MAX_LENGTH = 72;

    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.length() < MIN_LENGTH) {
            return fail(context, "Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (value.length() > MAX_LENGTH) {
            return fail(context, "Password must be at most " + MAX_LENGTH + " characters.");
        }
        if (!STRONG_PASSWORD_PATTERN.matcher(value).matches()) {
            return fail(context, "Password must contain uppercase, lowercase, number and special character.");
        }
        return true;
    }

    private static boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }

}
