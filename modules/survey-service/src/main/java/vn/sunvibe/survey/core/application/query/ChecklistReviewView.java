package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewAccess;

/**
 * The review part of a checklist read (survey-checklist submission spec 7.2): the submission once there is one, the
 * decision once made, what the viewer can do about it and, for a PASSED revision, whether it can still be used and why
 * it was withdrawn (property-create requalification spec 4). Any reader of the lead sees it (spec 4.2). The texts of the
 * decision and of the mark are private: never logged.
 */
public record ChecklistReviewView(SubmissionView submissionOrNull, ChecklistDecision decisionOrNull,
                                  ReviewAccess review, PassedView passedOrNull,
                                  ChecklistRequalification requalificationOrNull) {

    @Override
    public String toString() {
        return "ChecklistReviewView[hidden]";
    }
}
