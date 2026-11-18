package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One row of the staff agreement list, as it stands at the read (E05-S7 spec S7-D3, S7-D5, section 4). No owner, lead,
 * intake, property or user id, no term, fee, contract number or reason (brokerage-agreement BR-17).
 */
@Schema(name = "BrokerageAgreementListItem")
public record BrokerageAgreementListItemResponse(
        String agreementId,
        @Schema(nullable = true, description = "The unit's code, kept on the agreement since its creation; null without"
                + " one")
        String unitCode,
        @Schema(allowableValues = {"LONG_TERM_RENT"})
        String transactionType,
        @Schema(description = "The agreement's state at the read", allowableValues = {"DRAFTING",
                "AWAITING_CONFIRMATION", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED", "CANCELLED"})
        String status,
        @Schema(description = "The highest version number")
        int latestVersionNo,
        @Schema(description = "That version's state at the read", allowableValues = {"DRAFT", "AWAITING_OWNER",
                "OWNER_CONFIRMED", "ACTIVATED", "EFFECTIVE", "SUPERSEDED", "OWNER_REJECTED", "LAPSED",
                "ACTIVATION_REFUSED", "CANCELLED"})
        String latestVersionStatus,
        Instant createdAt,
        BrokerageAgreementListAdvertisingResponse advertising) {
}
