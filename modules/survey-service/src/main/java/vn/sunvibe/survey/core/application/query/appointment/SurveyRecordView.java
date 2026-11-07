package vn.sunvibe.survey.core.application.query.appointment;

import vn.sunvibe.survey.core.application.query.common.PassedView;
import vn.sunvibe.survey.core.domain.policy.checklist.RequalificationState;

/**
 * The survey record of the lead's current cycle on the survey block (survey-checklist submission spec 4.7, 7.2): what the
 * status menu needs, with a PASSED revision whether it can still be used, and whether the viewer can withdraw it
 * (property-create requalification spec 7).
 */
public record SurveyRecordView(boolean hasPassed, boolean hasSubmitted, PassedView passedOrNull,
                               RequalificationState requalification) {
}
