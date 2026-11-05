package vn.sunvibe.property.core.application.usecase.intake;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * One duplicate case for a manager (property-create intake spec 7.2). It holds an address and the manager's basis, so it stays out
 * of every log. Whether the owners match is read each time; no user id shows.
 *
 * @param outcome null until a decision
 * @param basis   null until a decision
 */
public record InventoryCaseView(String caseId,
                                @Schema(allowableValues = {"OPEN", "DECIDED", "CLOSED"}) String status,
                                @Schema(allowableValues = {"ADDRESS", "PIN", "INTAKE", "LINK_PLACE"}) String signal,
                                @Schema(nullable = true, allowableValues = {"DIFFERENT_ASSET", "SAME_ASSET"}) String outcome,
                                @Schema(nullable = true) String basis, @Schema(nullable = true) Instant decidedAt,
                                IntakeSide intake, Candidate candidate, long version) {

    @Schema(name = "InventoryCaseIntake")
    public record IntakeSide(String intakeId, String leadCode, String wardName, List<OwnerRequestSnapshot.Part> parts) {
    }

    /**
     * @param kind         PROPERTY or INTAKE
     * @param intakeId     null for a property
     * @param code         the property's code, or the lead's
     * @param ownerMatches null with no checked proof
     */
    @Schema(name = "InventoryCaseCandidate")
    public record Candidate(@Schema(allowableValues = {"PROPERTY", "INTAKE"}) String kind,
                            @Schema(nullable = true) String intakeId, String code, Boundary boundary,
                            List<OwnerRequestSnapshot.Part> units,
                            @Schema(nullable = true, description = "Null with no checked proof") Boolean ownerMatches) {
    }

    @Schema(name = "InventoryCaseBoundary")
    public record Boundary(@Schema(nullable = true) String assetType, @Schema(nullable = true) String layout, String wardName,
                           @Schema(nullable = true) String addressText) {

        @Override
        public String toString() {
            return "Boundary[" + assetType + "]";
        }
    }

    @Override
    public String toString() {
        return "InventoryCaseView[" + caseId + "]";
    }
}
