package vn.sunvibe.auth.core.presentation.validation.user.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import vn.sunvibe.auth.core.presentation.dto.request.auth.LoginRequest;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ExactlyOneLoginIdentifier;

/** Checks {@link ExactlyOneLoginIdentifier}. */
public class ExactlyOneLoginIdentifierValidator implements ConstraintValidator<ExactlyOneLoginIdentifier, LoginRequest> {

    @Override
    public boolean isValid(LoginRequest request, ConstraintValidatorContext context) {
        if (request == null || (request.email() == null) != (request.username() == null)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("email")
                .addConstraintViolation();
        return false;
    }

}
