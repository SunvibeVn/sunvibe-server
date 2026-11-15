package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Whether each unit of a lead's latest completed intake can get a brokerage agreement (E05-S1 spec 4). It names no lead,
 * owner or ownership row (brokerage-agreement BR-17).
 */
@Schema(name = "BrokerageEligibility")
public record BrokerageEligibilityResponse(
        @Schema(allowableValues = {"NONE", "USABLE", "SUSPENDED", "ENDED"},
                description = "The readiness state of the lead's latest completed intake; NONE without one")
        String basisState,
        @Schema(description = "The caller is the lead's current assignee and holds SALE or SALE_LEADER")
        boolean viewerIsAssignedSale,
        @Schema(description = "One per unit the intake maps, in the readiness order; empty with NONE")
        List<BrokerageEligibilityUnitResponse> units) {
}
