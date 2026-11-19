package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
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
        List<BrokerageAgreementVersionResponse> versions,
        @Schema(format = "int64", description = "The data version a change of the agreement sends back as"
                + " expectedDataVersion")
        long dataVersion,
        @Schema(description = "Whether the caller is the basis lead's assigned Sale, who edits the draft; a hint, the"
                + " server decides")
        boolean viewerIsAssignedSale,
        @Schema(description = "Whether Sunvibe may advertise the unit now under this agreement, computed at this read;"
                + " always sent")
        BrokerageAgreementAdvertisingResponse advertising,
        @Schema(description = "Whether the caller holds SALE_LEADER, SALES_MANAGER or ADMIN and is neither the basis lead's"
                + " assignee nor an author of the version awaiting activation; a hint, the server decides; always sent")
        boolean viewerMayDecideActivation,
        @Schema(nullable = true, description = "The activation statement of the version awaiting activation now; null"
                + " when none awaits activation")
        BrokerageAgreementStatementResponse activationStatement,
        @Schema(nullable = true, description = "When the agreement ended: the end of its version for an expiry, the moment"
                + " of a termination or a cancellation; null while it has not ended")
        Instant endedAt,
        @Schema(nullable = true, description = "What staff recorded when they terminated the agreement; null unless"
                + " TERMINATED; private")
        BrokerageAgreementTerminationResponse termination,
        @ArraySchema(arraySchema = @Schema(description = "The suspension records, newest first, at most 20; an absent"
                + " list reads as none"))
        List<BrokerageAgreementSuspensionResponse> suspensions,
        @Schema(description = "Whether older suspension records exist; their events stay in the paged history")
        boolean moreSuspensions,
        @ArraySchema(arraySchema = @Schema(description = "The stops the caller may take now, in this order; a hint, the"
                + " server decides; empty on an agreement that ended"),
                schema = @Schema(allowableValues = {"CANCEL", "SUSPEND", "RELEASE_SUSPENSION", "TERMINATE_OWNER_WITHDREW",
                        "TERMINATE_UNIT_LET", "TERMINATE_SUNVIBE"}))
        List<String> viewerStopActions) {
}
