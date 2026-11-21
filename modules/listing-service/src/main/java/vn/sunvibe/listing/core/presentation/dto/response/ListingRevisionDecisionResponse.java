package vn.sunvibe.listing.core.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The decision of a revision (listing-publish US-4 AC-6; E06-S4 spec section 4.4, E06S4-D15): outcome, time, checklist
 * version, reasons, group and note; never who decided. The reasons, group and note are private: its text names the
 * outcome only.
 */
@Schema(name = "ListingRevisionDecision")
public record ListingRevisionDecisionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"APPROVED", "NEEDS_CHANGES",
                "REJECTED"})
        String outcome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant decidedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "int32",
                description = "The checklist version; set for APPROVED")
        Integer checklistVersion,
        @ArraySchema(schema = @Schema(allowableValues = {"CONTENT_MISMATCH", "TEXT_RULE_VIOLATION",
                "MISSING_INFORMATION", "TERMS_MISMATCH", "OTHER"}), arraySchema = @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED, description = "Empty unless NEEDS_CHANGES"))
        List<String> changeReasons,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"SUSPECTED_FRAUD",
                "WRONG_UNIT", "SERIOUS_VIOLATION", "OTHER"}, description = "Set for REJECTED")
        String rejectionGroup,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
                description = "Private; set for NEEDS_CHANGES and REJECTED")
        String note) {

    @Override
    public String toString() {
        return "ListingRevisionDecisionResponse[" + outcome + "]";
    }
}
