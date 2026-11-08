package vn.sunvibe.property.core.application.usecase.intake;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The intake block of a lead (property-create intake spec 7.1, exact keys). Enum values are their names. It never holds a
 * user id, email or phone; a space's code and location and the G8 terms come from the survey, so it is not logged. The schema names
 * are explicit: springdoc keys a schema by its simple name, so two records called Target would become one.
 */
public record IntakeBlockView(
        @Schema(nullable = true, description = "Null with no current row",
                allowableValues = {"DRAFT", "AWAITING_OWNER", "OWNER_CONFIRMED", "CASE_OPEN", "COMPLETED"}) String status,
        @Schema(nullable = true, description = "Null with no current row") Long version,
        Gate gate,
        @Schema(nullable = true) EarlierResult earlierResult,
        @Schema(nullable = true, description = "Null with no current unmarked PASSED") Source source,
        @Schema(nullable = true) Selection selection,
        @Schema(allowableValues = {"NONE", "WAITING", "CONFIRMED"}) String ownerState,
        @Schema(allowableValues = {"NONE", "REQUIRED", "DONE"}) String ownerCheck,
        @Schema(nullable = true) Target target,
        @Schema(nullable = true) String forcedPropertyCode,
        List<Availability> availability,
        @Schema(allowableValues = {"NONE", "OPEN", "WAITING", "DECIDED"}) String caseState,
        @Schema(nullable = true) Result result,
        Readiness readiness,
        @Schema(description = "Only for manager roles") List<Suspension> suspensions) {

    @Schema(name = "IntakeGate")
    public record Gate(boolean ready,
                       @ArraySchema(schema = @Schema(allowableValues = {"NOT_LONG_TERM", "NOT_QUALIFIED", "NO_PASSED",
                               "PASSED_NOT_USABLE", "TYPE_SCOPE_MISMATCH", "RECONCILIATION_REQUIRED", "RESTRICTED"}))
                       List<String> reasons) {
    }

    @Schema(name = "IntakeEarlierResult")
    public record EarlierResult(@Schema(nullable = true) String propertyCode) {
    }

    @Schema(name = "IntakeBlockSource")
    public record Source(String revisionId,
                         @Schema(allowableValues = {"WHOLE", "PARTS"}) String rentalScope,
                         @Schema(nullable = true, allowableValues = {"HOUSE", "WHOLE_BUILDING", "VILLA", "HOMESTAY", "CONDOTEL",
                                 "APARTMENT"}) String assetType,
                         List<Space> spaces, List<Term> unconfirmedTerms) {
    }

    @Schema(name = "IntakeBlockSpace")
    public record Space(String spaceId, String code, @Schema(nullable = true) String location,
                        @Schema(nullable = true) String accessLimits, @Schema(nullable = true) LocalDate sourceDate) {
    }

    /** One G8 term, a reference only: never an accepted price (property-create BR-10). */
    @Schema(name = "IntakeBlockTerm")
    public record Term(String name, @Schema(nullable = true, description = "Null when the survey left it unknown") TermValue value,
                       @Schema(nullable = true) String source, @Schema(nullable = true) LocalDate date) {
    }

    /** Money stays amount, currency and unit (ADR-006); anything else is text. */
    @Schema(name = "IntakeTermValue")
    public record TermValue(@Schema(nullable = true) Long amount, @Schema(nullable = true) String currency,
                            @Schema(nullable = true) String unit, @Schema(nullable = true) String text) {
    }

    @Schema(name = "IntakeBlockSelection")
    public record Selection(List<String> spaceIds) {
    }

    @Schema(name = "IntakeBlockTarget")
    public record Target(@Schema(allowableValues = {"CREATE", "LINK"}) String mode, @Schema(nullable = true) String propertyCode,
                         @Schema(nullable = true) String canonicalSpaceId) {
    }

    @Schema(name = "IntakeBlockAvailability")
    public record Availability(String spaceId,
                               @Schema(allowableValues = {"VACANT", "OCCUPIED", "UNDER_REPAIR", "PAUSED", "OWNER_UNSURE",
                                       "SOURCE_CONFLICT"}) String state,
                               LocalDate contactedOn, String note, @Schema(nullable = true) LocalDate expectedDate) {
    }

    @Schema(name = "IntakeBlockResult")
    public record Result(@Schema(nullable = true) String propertyCode, int unitCount, Instant completedAt) {
    }

    @Schema(name = "IntakeReadiness")
    public record Readiness(@Schema(allowableValues = {"NONE", "USABLE", "SUSPENDED", "ENDED"}) String state,
                            @ArraySchema(schema = @Schema(allowableValues = {"LEAD_ON_HOLD", "EVIDENCE_HELD", "OWNER_NOT_CONFIRMED",
                                    "OWNER_NOT_ELIGIBLE", "RESTRICTED", "DUPLICATE", "REJECTED", "WITHDRAWN", "REQUALIFIED",
                                    "INTENT_CHANGED", "EVIDENCE_GONE"})) List<String> reasons,
                            @Schema(nullable = true, description = "Null with no result") String sourceRevisionId,
                            List<ReadinessUnit> units,
                            @ArraySchema(schema = @Schema(allowableValues = {"AGREEMENT", "ADVERTISING_PERMISSION", "CONFIRMED_TERMS",
                                    "PHOTO_RIGHTS"})) List<String> missingForBrokerage) {
    }

    @Schema(name = "IntakeReadinessUnit")
    public record ReadinessUnit(String unitId, @Schema(nullable = true) String unitCode, boolean usable,
                                @ArraySchema(schema = @Schema(allowableValues = {"RESTRICTED", "AVAILABILITY_DIFFERS"}))
                                List<String> reasons) {
    }

    /** {@code reason} is left out for an assignee, also one who holds a manager role (property-create intake spec 7.1). */
    @Schema(name = "IntakeBlockSuspension")
    public record Suspension(String id, @Schema(nullable = true, description = "Null for the whole lead") String unitCode,
                             @Schema(description = "Left out for an assignee who holds a manager role")
                             @JsonInclude(JsonInclude.Include.NON_NULL) String reason, Instant createdAt) {
    }

}
