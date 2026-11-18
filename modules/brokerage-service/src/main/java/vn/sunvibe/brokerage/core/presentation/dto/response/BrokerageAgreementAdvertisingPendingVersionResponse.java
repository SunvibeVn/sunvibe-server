package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An activated version that has not started yet (brokerage-agreement US-4 AC-1; E05-S6 spec S6-D7): its start and its
 * rental terms.
 */
@Schema(name = "BrokerageAgreementAdvertisingPendingVersion")
public record BrokerageAgreementAdvertisingPendingVersionResponse(
        @Schema(description = "A calendar date in Vietnam time")
        LocalDate startDate,
        @Schema(description = "The start of that date in Vietnam")
        Instant startsAt,
        BrokerageAgreementTermsResponse terms) {

    /** brokerage-agreement BR-17: the terms stay out of every log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementAdvertisingPendingVersionResponse[" + startDate + "]";
    }
}
