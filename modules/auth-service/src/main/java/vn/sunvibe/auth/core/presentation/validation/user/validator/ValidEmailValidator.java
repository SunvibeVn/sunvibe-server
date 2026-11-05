package vn.sunvibe.auth.core.presentation.validation.user.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.vo.Email;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidEmail;

/**
 * Checks {@link ValidEmail} by asking {@link Email} itself, so the API has no email rule of its own
 * (auth-email-access BR-23).
 */
public class ValidEmailValidator implements ConstraintValidator<ValidEmail, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            Email.create(value);
            return true;
        } catch (AuthValidationException refused) {
            return false;
        }
    }

}
