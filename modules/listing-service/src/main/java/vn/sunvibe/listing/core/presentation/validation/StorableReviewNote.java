package vn.sunvibe.listing.core.presentation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import vn.sunvibe.listing.core.domain.model.listing.ReviewNote;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A review note the decision can store (listing-publish US-4 AC-3; E06-S4 spec E06S4-D6): at most
 * {@link ReviewNote#EDGE_UNITS} UTF-16 units as sent, checked before the text is normalized, then storable in its stored
 * form. Answered with the request's shape, before the role check; the rule lives in {@link ReviewNote} only. Null passes;
 * whether the outcome needs a note is the request's cross-field rule.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StorableReviewNote.Validator.class)
public @interface StorableReviewNote {

    String message() default "This value is not accepted.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Bounds the text first, so a long text is never normalized, then checks it in the form the decision stores. */
    class Validator implements ConstraintValidator<StorableReviewNote, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || (value.length() <= ReviewNote.EDGE_UNITS
                    && ReviewNote.storable(ReviewNote.normalized(value)));
        }
    }
}
