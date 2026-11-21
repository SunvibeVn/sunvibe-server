package vn.sunvibe.listing.core.presentation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessNote;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A freshness note the record can store (listing-publish BR-17; E06-S3 spec E06S3-D10): at most
 * {@link FreshnessNote#EDGE_UNITS} UTF-16 units as sent, checked before the text is normalized, then storable in its
 * stored form. Answered with the request's shape, before the role check; the rule lives in {@link FreshnessNote} only.
 * Null passes.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StorableFreshnessNote.Validator.class)
public @interface StorableFreshnessNote {

    String message() default "This value is not accepted.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Bounds the text first, so a long text is never normalized, then checks it in the form the record stores. */
    class Validator implements ConstraintValidator<StorableFreshnessNote, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || (value.length() <= FreshnessNote.EDGE_UNITS
                    && FreshnessNote.storable(FreshnessNote.normalized(value)));
        }
    }
}
