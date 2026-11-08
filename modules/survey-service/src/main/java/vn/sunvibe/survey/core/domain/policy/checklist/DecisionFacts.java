package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.ConditionContent;
import vn.sunvibe.survey.core.domain.model.checklist.IdentificationContent;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * What the PASSED rules read (survey-checklist submission spec 4.5, I-6): the warnings of the revision, its frozen
 * photos as read under the locks, the photos the reviewer rejects, a SCHEDULED appointment on the lead and the frozen
 * G1 and G4.
 */
public record DecisionFacts(List<PreviewWarning> warnings, Collection<SurveyMedia> frozenPhotos,
                            Set<String> rejectedMediaIds, boolean scheduledAppointment, IdentificationContent g1,
                            ConditionContent g4OrNull) {
}
