package vn.sunvibe.auth.core.presentation.validation.user.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.vo.PhoneNumber;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ValidPhone;

/**
 * Checks {@link ValidPhone} by asking {@link PhoneNumber} itself, so the API has no phone rule of its own
 * (auth-email-access BR-23).
 */
public class ValidPhoneValidator implements ConstraintValidator<ValidPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            PhoneNumber.create(value);
            return true;
        } catch (AuthValidationException refused) {
            return false;
        }
    }

}
