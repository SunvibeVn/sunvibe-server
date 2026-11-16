package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * Draft a brokerage agreement for one unit of the lead's intake basis (E05-S2 spec 4). Unknown properties are ignored:
 * no field sets a status, an owner, an actor or a time.
 */
public record BrokerageAgreementCreateRequest(
        @NotNull @Pattern(regexp = "^[0-9A-Z]{26}$")
        @Schema(description = "The basis lead: the owner lead whose latest completed intake maps the unit")
        String leadId,
        @NotNull @Pattern(regexp = "^[0-9A-Z]{26}$")
        @Schema(description = "A unit the lead's latest completed intake maps")
        String unitId,
        @NotNull
        @Schema(allowableValues = {"LONG_TERM_RENT"}, description = "Only long-term rental now")
        String transactionType,
        @NotNull
        @Schema(description = "A calendar date in Vietnam time, 2000-01-01 to 2099-12-31")
        LocalDate startDate,
        @NotNull
        @Schema(description = "A calendar date in Vietnam time, not before the start date and at most 12 months after it")
        LocalDate endDate) {
}
