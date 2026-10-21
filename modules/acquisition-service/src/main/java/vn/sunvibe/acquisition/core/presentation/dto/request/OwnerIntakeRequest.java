package vn.sunvibe.acquisition.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO - OwnerIntakeRequest (Record)
 * <p>
 * Body of the public owner intake form (acquisition-owner-intake spec 6.3). The checks here need no lookup; the
 * phone format, the ward of the province, the unit for the intent, the consent notice version and the coordinates
 * are checked by the service and reported on the same fields. Messages are English and never repeat the value.
 */
@Schema(description = "Owner intake form: a property owner asks Sunvibe to call back")
public record OwnerIntakeRequest(
        // 1 to 100 characters once the spaces around the name are removed; a blank name passes the pattern so it
        // is reported only as missing
        @NotBlank(message = "Contact name is required.")
        @Pattern(regexp = "\\s*(\\S(.{0,98}\\S)?\\s*)?", flags = Pattern.Flag.DOTALL,
                message = "Contact name must be at most 100 characters.")
        @Schema(example = "Nguyễn Thị Lan", maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
        String contactName,

        @NotBlank(message = "Phone is required.")
        @Schema(description = "Vietnamese phone number: 0 or +84, then 9 digits; spaces, dots, dashes and "
                + "parentheses are ignored", example = "0909 123 456", requiredMode = Schema.RequiredMode.REQUIRED)
        String phone,

        // acquisition-owner-intake spec 6.3: Hibernate @Email accepts a@b, so the domain must also hold a dot. The
        // domain labels exclude the dot, so the pattern can split the text only one way and runs in linear time
        @Size(max = 254, message = "Email must be at most 254 characters.")
        @Email(message = "Email is not a valid email address.")
        @Pattern(regexp = "|[^@]+@[^@.]+(\\.[^@.]+)+", message = "Email is not a valid email address.")
        @Schema(example = "lan@example.com", maxLength = 254)
        String email,

        @NotNull(message = "Intent is required.")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OwnerLeadIntent intent,

        @NotBlank(message = "Province code is required.")
        @Schema(example = "48", requiredMode = Schema.RequiredMode.REQUIRED)
        String provinceCode,

        @NotBlank(message = "Ward code is required.")
        @Schema(example = "20242", requiredMode = Schema.RequiredMode.REQUIRED)
        String wardCode,

        @NotBlank(message = "Address detail is required.")
        @Size(max = 255, message = "Address detail must be at most 255 characters.")
        @Schema(example = "12 Bạch Đằng, căn A-1203", maxLength = 255, requiredMode = Schema.RequiredMode.REQUIRED)
        String addressDetail,

        // A cheap guard only: these checks compare the exponent and never write the number out, so 1e999999999 is
        // refused here at once. The area Sunvibe serves and the rounding to 6 decimals are checked by the domain
        @DecimalMin(value = "-90", message = "Latitude must be a number from -90 to 90.")
        @DecimalMax(value = "90", message = "Latitude must be a number from -90 to 90.")
        @Digits(integer = 2, fraction = 10, message = "Latitude must have at most 10 decimals.")
        @Schema(description = "Give both latitude and longitude, or neither", example = "16.0678")
        BigDecimal latitude,

        @DecimalMin(value = "-180", message = "Longitude must be a number from -180 to 180.")
        @DecimalMax(value = "180", message = "Longitude must be a number from -180 to 180.")
        @Digits(integer = 3, fraction = 10, message = "Longitude must have at most 10 decimals.")
        @Schema(example = "108.2208")
        BigDecimal longitude,

        OwnerLeadPropertyType propertyType,

        @Valid
        AskingPriceRequest askingPrice,

        @Schema(example = "2026-11-01")
        LocalDate availableFrom,

        @Size(max = 1000, message = "Notes must be at most 1000 characters.")
        @Schema(maxLength = 1000)
        String notes,

        @Size(max = 100, message = "Preferred callback time must be at most 100 characters.")
        @Schema(example = "Buổi tối", maxLength = 100)
        String preferredCallbackTime,

        @NotNull(message = "Consent is required.")
        @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ConsentRequest consent) {

    /** The body is personal data (contact, address, position): never print it. */
    @Override
    public String toString() {
        return "OwnerIntakeRequest[hidden]";
    }

    /**
     * Price the owner hopes for, in whole VND (acquisition-owner-intake BR-4). Leave it out entirely, or give at
     * least the amount; the unit must fit the intent (night, month or total), which the service checks.
     */
    @PriceAmountRequired
    public record AskingPriceRequest(
            // Decimal type so that 1500.5 or 1500.0 reach the check and are refused here, on this field
            @DecimalMin(value = "1", message = "Asking price amount must be a whole number from 1 to 15 digits.")
            @Digits(integer = 15, fraction = 0,
                    message = "Asking price amount must be a whole number from 1 to 15 digits.")
            @Schema(type = "integer", format = "int64", example = "15000000", minimum = "1",
                    maximum = "999999999999999")
            BigDecimal amount,

            @Pattern(regexp = "VND", message = "Asking price currency must be VND.")
            @Schema(example = "VND", allowableValues = "VND")
            String currency,

            PriceUnit unit) {
    }

    /** Consent to be contacted about this request (acquisition-owner-intake BR-9). */
    public record ConsentRequest(
            @NotNull(message = "Consent to be contacted must be accepted.")
            @AssertTrue(message = "Consent to be contacted must be accepted.")
            @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            Boolean accepted,

            @NotBlank(message = "Consent notice version is required.")
            @Schema(description = "Version of the consent notice the owner was shown",
                    example = "owner-intake-2026-10-21", requiredMode = Schema.RequiredMode.REQUIRED)
            String noticeVersion) {
    }
}
