package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The detail of a brokerage agreement (E05-S2 spec 4). It names no owner, intake, property, creator or drafter
 * (brokerage-agreement BR-17).
 */
@Schema(name = "BrokerageAgreementDetail")
public record BrokerageAgreementDetailResponse(
        String agreementId,
        @Schema(allowableValues = {"DRAFTING", "AWAITING_CONFIRMATION", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED",
                "CANCELLED"})
        String status,
        @Schema(allowableValues = {"LONG_TERM_RENT"})
        String transactionType,
        String unitId,
        @Schema(nullable = true, description = "The unit's code as read from the intake basis at creation; null when it"
                + " had none")
        String unitCode,
        @Schema(description = "The basis lead of the latest version")
        String basisLeadId,
        Instant createdAt,
        @Schema(description = "By version number, from 1")
        List<BrokerageAgreementVersionResponse> versions) {
}
