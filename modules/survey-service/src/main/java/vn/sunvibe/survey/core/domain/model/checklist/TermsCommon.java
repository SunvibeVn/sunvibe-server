package vn.sunvibe.survey.core.domain.model.checklist;

import java.util.List;

/**
 * The common G8 terms (survey-checklist spec 4.6): a value here means the source said it applies to every space.
 */
public record TermsCommon(SourcedField<Charge> electricity, SourcedField<Charge> water, SourcedField<Charge> internet,
                          SourcedField<Charge> parking, SourcedField<Charge> serviceFee,
                          SourcedField<List<Fee>> otherFees, SourcedField<String> houseRules, SourcedField<Pets> pets,
                          SourcedField<String> handoverMaintenance) {

    @Override
    public String toString() {
        return "TermsCommon[hidden]";
    }
}
