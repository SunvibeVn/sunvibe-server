package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;

import java.util.Collection;
import java.util.List;

/**
 * What the submission gates read (survey-checklist submission spec 4.4): the two switches, a SCHEDULED appointment on
 * the lead, the readiness items and every photo of the revision, own and carried.
 */
public record GateFacts(boolean uploadsOn, boolean submissionOn, boolean scheduledAppointment,
                        List<MissingItem> missing, Collection<SurveyMedia> photos) {
}
