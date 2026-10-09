package vn.sunvibe.auth.core.presentation.validation.user.constraint;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import vn.sunvibe.auth.core.presentation.validation.user.validator.StrongPasswordValidator;

import java.lang.annotation.*;

/**
 * The annotated string must be a strong password; see {@code StrongPasswordValidator}.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Please select the strong password.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
