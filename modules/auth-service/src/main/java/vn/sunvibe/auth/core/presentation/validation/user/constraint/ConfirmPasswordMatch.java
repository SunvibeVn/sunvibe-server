package vn.sunvibe.auth.core.presentation.validation.user.constraint;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import vn.sunvibe.auth.core.presentation.validation.user.validator.PasswordMatchValidator;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordMatchValidator.class)
@Documented
public @interface ConfirmPasswordMatch {

    String message() default "Confirm password must match password.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    // password field name
    String password() default "password";

    // confirm password field name
    String confirmPassword() default "confirmPassword";
}
