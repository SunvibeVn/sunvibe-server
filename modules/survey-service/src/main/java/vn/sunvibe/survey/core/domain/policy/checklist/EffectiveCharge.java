package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.Charge;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;

/** The charge a space gets and where it comes from (survey-checklist spec 4.7, 7.2). */
public record EffectiveCharge(SourcedField<Charge> term, ChargeOrigin origin) {
}
