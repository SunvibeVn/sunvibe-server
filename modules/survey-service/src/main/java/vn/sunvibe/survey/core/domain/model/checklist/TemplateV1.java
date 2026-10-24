package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/**
 * The long-term rental checklist template, version 1 (survey-checklist spec 4.6, A-15): its code and version and the
 * empty content of each stored group. Empty means every field UNKNOWN, rental scope and notes null and per-space lists
 * empty; units only separate parts have stay null (spec PD-4).
 */
public final class TemplateV1 {

    public static final String CODE = "LONG_TERM_RENT";
    public static final int VERSION = 1;

    private TemplateV1() {
    }

    /** The content of a group of a new draft. */
    public static GroupContent empty(ChecklistGroup group) {
        return switch (group) {
            case IDENTIFICATION -> new IdentificationContent(SourcedField.unknown(), null, Field.unknown(),
                    Field.unknown(), null, List.of(), null, null);
            case PARTS -> new PartsContent(Field.unknown(), Field.unknown(), SourcedField.unknown(), List.of());
            case DIMENSIONS -> new DimensionsContent(List.of());
            case CONDITION -> new ConditionContent(null, null, List.of());
            case SAFETY -> new SafetyContent(Field.unknown(), Field.unknown(), Field.unknown());
            case AMENITIES -> new AmenitiesContent(List.of(), List.of(), List.of());
            case TERMS -> new TermsContent(new TermsCommon(SourcedField.unknown(), SourcedField.unknown(),
                    SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(),
                    SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown()), List.of());
            case EVIDENCE -> throw new IllegalArgumentException("EVIDENCE has no content in template v1.");
        };
    }

    /** The G2 entry of a space nothing was recorded for. */
    public static PartsSpace emptyPartsSpace(String spaceId) {
        return new PartsSpace(spaceId, Field.unknown(), Field.unknown(), Field.unknown());
    }

    /** The G4 entry of a space nothing was recorded for. */
    public static ConditionSpace emptyConditionSpace(String spaceId) {
        return new ConditionSpace(spaceId, Field.unknown(), SourcedField.unknown(), SourcedField.unknown(),
                Field.unknown(), Field.unknown(), Field.unknown());
    }

    /** The G6 entry of a space nothing was recorded for. */
    public static AmenitiesSpace emptyAmenitiesSpace(String spaceId) {
        return new AmenitiesSpace(spaceId, SourcedField.unknown(), List.of());
    }

    /**
     * The G8 entry of a space nothing was recorded for: in PARTS five null charge overrides (the common charges
     * apply) and no space fees; otherwise neither (spec PD-4, PD-5).
     */
    public static TermsSpace emptyTermsSpace(String spaceId, RentalScope scope) {
        return new TermsSpace(spaceId, SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(),
                SourcedField.unknown(), scope == RentalScope.PARTS ? ChargeOverrides.none() : null, null);
    }

    /** The G3 entry of a space nothing was recorded for. */
    public static DimensionsSpace emptyDimensionsSpace(String spaceId) {
        return new DimensionsSpace(spaceId, SourcedField.unknown(), SourcedField.unknown(), SourcedField.unknown(),
                SourcedField.unknown(), null);
    }
}
