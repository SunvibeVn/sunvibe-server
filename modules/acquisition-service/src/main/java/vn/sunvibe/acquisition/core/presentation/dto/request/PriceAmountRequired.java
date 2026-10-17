package vn.sunvibe.acquisition.core.presentation.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest.AskingPriceRequest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An asking price is left out entirely or has its amount: a currency or unit without an amount is reported on
 * {@code askingPrice.amount}, the field the service uses for the same case.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PriceAmountRequired.Validator.class)
public @interface PriceAmountRequired {

    String message() default "Asking price amount is required.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Puts the violation on the {@code amount} property of the price. */
    class Validator implements ConstraintValidator<PriceAmountRequired, AskingPriceRequest> {

        @Override
        public boolean isValid(AskingPriceRequest price, ConstraintValidatorContext context) {
            if (price == null || price.amount() != null || (price.currency() == null && price.unit() == null)) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("amount")
                    .addConstraintViolation();
            return false;
        }
    }
}
