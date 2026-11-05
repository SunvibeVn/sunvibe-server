package vn.sunvibe.auth.core.presentation.validation.user.constraint;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import vn.sunvibe.auth.core.presentation.validation.user.validator.ExactlyOneLoginIdentifierValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level check of a login body: exactly one of {@code email} and {@code username}. The violation is reported on
 * {@code email} (auth-email-access spec 7.2).
 */
@Documented
@Constraint(validatedBy = ExactlyOneLoginIdentifierValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExactlyOneLoginIdentifier {

    String message() default "Enter an email.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
