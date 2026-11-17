package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One agreement as its owner reads it (brokerage-agreement US-2 AC-1; E05-S5 spec S5-D1, 4). No owner, lead, intake,
 * property or creator id and no staff name (brokerage-agreement BR-17).
 */
@Schema(name = "BrokerageAgreementOwnerItem")
public record BrokerageAgreementOwnerItemResponse(
        String agreementId,
        @Schema(allowableValues = {"DRAFTING", "AWAITING_CONFIRMATION", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED",
                "CANCELLED"}, description = "The effective state: past a version's deadline the agreement reads"
                + " DRAFTING")
        String status,
        @Schema(allowableValues = {"LONG_TERM_RENT"})
        String transactionType,
        @Schema(nullable = true, description = "The unit's code; null when the unit has none")
        String unitCode,
        @Schema(nullable = true, description = "The unit's location in the property; null when none is known")
        String unitLocation,
        @Schema(nullable = true, description = "The property's street; null when the property no longer answers")
        String street,
        @Schema(nullable = true, description = "The property's ward; null when the property no longer answers")
        String wardName,
        @Schema(description = "The versions sent to the owner, by version number, in their effective state")
        List<BrokerageAgreementVersionResponse> versions,
        @Schema(nullable = true, description = "The statement of the version the owner may answer now; null when none")
        BrokerageAgreementStatementResponse statement) {
}
