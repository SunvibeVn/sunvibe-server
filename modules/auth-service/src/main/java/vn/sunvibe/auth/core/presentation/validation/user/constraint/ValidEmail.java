package vn.sunvibe.auth.core.presentation.validation.user.constraint;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import vn.sunvibe.auth.core.presentation.validation.user.validator.ValidEmailValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must pass the email policy of {@code Email.create}. Null passes: pair it with {@code @NotNull}
 * or {@code @NotBlank} where the field is required.
 */
@Documented
@Constraint(validatedBy = ValidEmailValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {

    String message() default "Email is invalid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
