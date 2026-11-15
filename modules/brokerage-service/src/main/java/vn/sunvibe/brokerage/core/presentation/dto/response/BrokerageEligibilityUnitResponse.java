package vn.sunvibe.brokerage.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** One unit of a lead's latest completed intake and whether it can get a brokerage agreement now (E05-S1 spec 4). */
@Schema(name = "BrokerageEligibilityUnit")
public record BrokerageEligibilityUnitResponse(
        String unitId,
        @Schema(nullable = true, description = "Null when the unit row is missing") String unitCode,
        boolean eligible,
        @ArraySchema(arraySchema = @Schema(description = "BASIS: the lead's intake result is not usable now; PROPERTY: a"
                + " stop for the whole property; UNIT: a stop of this unit; OWNER: the owner on record does not meet"
                + " brokerage-agreement BR-4. No group names a lead."),
                schema = @Schema(allowableValues = {"BASIS", "PROPERTY", "UNIT", "OWNER"}))
        List<String> reasonGroups) {
}
