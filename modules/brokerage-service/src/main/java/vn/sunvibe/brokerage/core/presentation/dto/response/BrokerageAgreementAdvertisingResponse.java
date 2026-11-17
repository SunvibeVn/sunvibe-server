package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Whether Sunvibe may advertise the agreement's unit now, computed at the read (brokerage-agreement BR-12, BR-13, US-4
 * AC-1, AC-2; E05-S6 spec S6-D7, S6-D8). Nothing else of the agreement: no contract number, fee policy, owner, lead,
 * intake, confirmation or activation date.
 */
@Schema(name = "BrokerageAgreementAdvertising")
public record BrokerageAgreementAdvertisingResponse(
        @Schema(description = "True exactly when reasonGroups is empty")
        boolean allowed,
        @ArraySchema(schema = @Schema(allowableValues = {"NO_AGREEMENT", "NOT_IN_EFFECT", "NOT_STARTED", "SUSPENDED",
                "ENDED", "BASIS", "PROPERTY", "UNIT", "OWNER"}), arraySchema = @Schema(description = "The groups of"
                + " reasons that stop the advertising, in this order; empty when allowed; NOT_STARTED alone means the"
                + " activated version has not started yet"))
        List<String> reasonGroups,
        @Schema(nullable = true, description = "The end date of the version that counts, a calendar date in Vietnam"
                + " time; null without one")
        LocalDate endDate,
        @Schema(nullable = true, description = "The start of the day after that end date in Vietnam; null without one")
        Instant endsAt,
        BrokerageAgreementAdvertisingPhotoConsentResponse photoConsent,
        @Schema(nullable = true, description = "The rental terms of the version in effect; null without one or once"
                + " ended")
        BrokerageAgreementTermsResponse termsInEffect,
        @Schema(nullable = true, description = "The activated version that has not started yet; null without one")
        BrokerageAgreementAdvertisingPendingVersionResponse pendingVersion) {

    /** brokerage-agreement BR-17: the terms stay out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementAdvertisingResponse[allowed=" + allowed + ", " + reasonGroups + "]";
    }
}
