package vn.sunvibe.acquisition.core.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.presentation.dto.request.OwnerIntakeRequest.AskingPriceRequest;

import java.time.LocalDate;
import java.util.Optional;

/**
 * DTO - ChangeLeadIntentRequest (Record)
 * <p>
 * Record a new requirement agreed with the owner (survey-schedule US-5, spec 7.1), guarded by the version the caller
 * last saw. {@code askingPrice} must be sent, as a price or as an explicit null for none (spec A-17). Whether the
 * intent differs from the current one and the basis date is not before the lead's creation are checked by the
 * service. Messages are English and never repeat the value.
 */
@Schema(description = "Record a new requirement (rental intent and price) of an owner lead")
public record ChangeLeadIntentRequest(
        @NotNull(message = "Expected version is required.")
        @PositiveOrZero(message = "Expected version must be 0 or more.")
        @Schema(description = "The lead version the caller last saw", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long expectedVersion,

        @NotNull(message = "New intent is required.")
        @Schema(description = "SHORT_TERM_RENT or LONG_TERM_RENT, different from the current intent",
                requiredMode = Schema.RequiredMode.REQUIRED)
        OwnerLeadIntent newIntent,

        @JsonProperty(required = true)
        @Schema(description = "The new price, its unit NIGHT for SHORT_TERM_RENT or MONTH for LONG_TERM_RENT; null "
                + "for none", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        Optional<@Valid AskingPriceRequest> askingPrice,

        @NotNull(message = "Reason is required.")
        @Size(max = 500, message = "Reason must be at most 500 characters.")
        @Schema(description = "Why the requirement changed; no phone number", maxLength = 500,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,

        @NotNull(message = "Basis is required.")
        @Valid
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        BasisRequest basis) {

    /** How the owner confirmed the new requirement. */
    @Schema(name = "LeadIntentChangeBasisRequest", description = "How the owner confirmed the new requirement")
    public record BasisRequest(
            @NotNull(message = "Confirmation date is required.")
            @Schema(description = "Vietnam date, from the lead's creation date to today",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate confirmedOn,

            @NotNull(message = "Basis text is required.")
            @Size(max = 500, message = "Basis text must be at most 500 characters.")
            @Schema(description = "No phone number", maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
            String text) {

        @Override
        public String toString() {
            return "BasisRequest[" + confirmedOn + "]";
        }
    }

    /** Reason and basis are staff text: never print them. */
    @Override
    public String toString() {
        return "ChangeLeadIntentRequest[hidden]";
    }
}
