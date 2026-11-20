package vn.sunvibe.listing.core.presentation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A listing title the revision can store (listing-publish BR-2; E06-S2 spec E06S2-D4): in its stored form (NFC, LF,
 * trimmed), at most 90 code points on one line, no control or format character. Answered with the request's shape,
 * before the role check; the bounds live in {@link RevisionContent} only. Null passes.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StorableListingTitle.Validator.class)
public @interface StorableListingTitle {

    String message() default "This value is not accepted.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Checks the title in the form the revision would store it. */
    class Validator implements ConstraintValidator<StorableListingTitle, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || RevisionContent.titleStorable(RevisionContent.normalized(value));
        }
    }
}
