package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.Fee;
import vn.sunvibe.survey.core.domain.model.checklist.SourcedField;

import java.util.List;

/**
 * The effective G8 values of one space in the preview (survey-checklist spec 4.7): each charge from the space's
 * override, else the common one; the fees are the common list plus the space's own list.
 */
public record SpaceTerms(String spaceId, EffectiveCharge electricity, EffectiveCharge water,
                         EffectiveCharge internet, EffectiveCharge parking, EffectiveCharge serviceFee,
                         SourcedField<List<Fee>> commonFees, SourcedField<List<Fee>> spaceFees) {
}
