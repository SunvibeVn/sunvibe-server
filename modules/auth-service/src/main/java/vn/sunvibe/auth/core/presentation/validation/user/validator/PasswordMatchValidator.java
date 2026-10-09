package vn.sunvibe.auth.core.presentation.validation.user.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;
import vn.sunvibe.auth.core.presentation.validation.user.constraint.ConfirmPasswordMatch;

/**
 * Checks {@link ConfirmPasswordMatch}.
 */
public class PasswordMatchValidator implements ConstraintValidator<ConfirmPasswordMatch, Object> {

    private String passwordFieldName;
    private String confirmPasswordFieldName;

    @Override
    public void initialize(ConfirmPasswordMatch constraintAnnotation) {
        this.passwordFieldName = constraintAnnotation.password();
        this.confirmPasswordFieldName = constraintAnnotation.confirmPassword();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;  // Null validation handle by @NotNull
        }

        try {
            // Get values of 2 fields
            Object passwordFieldValue = new BeanWrapperImpl(value)
                    .getPropertyValue(passwordFieldName);

            Object confirmPasswordFieldValue = new BeanWrapperImpl(value)
                    .getPropertyValue(confirmPasswordFieldName);

            // Compare 2 values
            if (passwordFieldValue == null && confirmPasswordFieldValue == null) {
                return true;  // Both null → pass (Will be caught by @NotNull)
            }

            if (passwordFieldValue == null || confirmPasswordFieldValue == null) {
                return false;  // 1 null 1 not null → fail
            }

            // Compare
            boolean isValid = passwordFieldValue.equals(confirmPasswordFieldValue);

            // Custom error message with  field name
            if (!isValid) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                context.getDefaultConstraintMessageTemplate()
                        )
                        .addPropertyNode(confirmPasswordFieldName)
                        .addConstraintViolation();
            }

            return isValid;

        } catch (Exception e) {
            // Log error if field not exists
            context.buildConstraintViolationWithTemplate(
                            "Unable to validate password fields: " + e.getMessage()
                    )
                    .addConstraintViolation();
            return false;
        }
    }
}
