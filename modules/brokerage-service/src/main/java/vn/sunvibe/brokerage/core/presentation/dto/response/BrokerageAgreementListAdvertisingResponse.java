package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Whether Sunvibe may advertise a listed agreement's unit now, computed at the read as the detail computes it
 * (brokerage-agreement BR-12, US-4 AC-1, AC-3; E05-S7 spec S7-D5). The dates only: no term, photo consent, fee, owner,
 * lead or intake (brokerage-agreement BR-17).
 */
@Schema(name = "BrokerageAgreementListAdvertising")
public record BrokerageAgreementListAdvertisingResponse(
        @Schema(description = "True exactly when reasonGroups is empty")
        boolean allowed,
        @ArraySchema(schema = @Schema(allowableValues = {"NO_AGREEMENT", "NOT_IN_EFFECT", "NOT_STARTED", "SUSPENDED",
                "ENDED", "BASIS", "PROPERTY", "UNIT", "OWNER"}), arraySchema = @Schema(description = "The groups of"
                + " reasons that stop the advertising, in this order; empty when allowed"))
        List<String> reasonGroups,
        @Schema(nullable = true, description = "The end date of the version that counts, a calendar date in Vietnam"
                + " time; null without one")
        LocalDate endDate,
        @Schema(nullable = true, description = "The start of the day after that end date in Vietnam; null without one")
        Instant endsAt,
        @Schema(nullable = true, description = "The start date of the activated version that has not started yet, a"
                + " calendar date in Vietnam time; null without one")
        LocalDate startDate) {
}
