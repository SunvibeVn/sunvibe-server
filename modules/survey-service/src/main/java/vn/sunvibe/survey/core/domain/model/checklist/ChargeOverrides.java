package vn.sunvibe.survey.core.domain.model.checklist;

/**
 * The charges that differ for one space in PARTS scope (survey-checklist BR-5 "chênh lệch gắn phần"): each member is
 * null (the common charge applies) or a charge term in state VALUE.
 */
public record ChargeOverrides(SourcedField<Charge> electricity, SourcedField<Charge> water,
                              SourcedField<Charge> internet, SourcedField<Charge> parking,
                              SourcedField<Charge> serviceFee) {

    /** No override: every member null. */
    public static ChargeOverrides none() {
        return new ChargeOverrides(null, null, null, null, null);
    }
}
