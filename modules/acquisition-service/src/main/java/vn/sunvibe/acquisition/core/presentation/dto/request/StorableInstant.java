package vn.sunvibe.acquisition.core.presentation.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;

/**
 * A time the database can store: from 1970-01-01T00:00:00Z up to the end of year 9999 (UTC). A technical range only,
 * not a business limit; outside it the value would fail on save instead of being refused on its field. Null passes.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StorableInstant.Validator.class)
public @interface StorableInstant {

    String message() default "Time must be from 1970-01-01T00:00:00Z to the end of year 9999.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Checks the instant against the storable range. */
    class Validator implements ConstraintValidator<StorableInstant, Instant> {

        static final Instant FIRST = Instant.EPOCH;
        static final Instant END = Instant.parse("+10000-01-01T00:00:00Z");

        @Override
        public boolean isValid(Instant value, ConstraintValidatorContext context) {
            return value == null || (!value.isBefore(FIRST) && value.isBefore(END));
        }
    }
}
