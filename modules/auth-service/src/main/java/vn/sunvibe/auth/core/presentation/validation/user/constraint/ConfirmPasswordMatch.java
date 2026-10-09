package vn.sunvibe.auth.core.presentation.validation.user.constraint;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import vn.sunvibe.auth.core.presentation.validation.user.validator.PasswordMatchValidator;

import java.lang.annotation.*;

/**
 * Class-level check that two properties (password and its confirmation) are equal.
 * The violation is reported on the confirmation property.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchValidator.class)
@Documented
public @interface ConfirmPasswordMatch {

    String message() default "Confirm password must match password.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Name of the password property. */
    String password() default "password";

    /** Name of the confirmation property. */
    String confirmPassword() default "confirmPassword";
}
