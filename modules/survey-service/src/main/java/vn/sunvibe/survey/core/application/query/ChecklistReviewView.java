package vn.sunvibe.survey.core.application.query;

import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewAccess;

/**
 * The review part of a checklist read (survey-checklist submission spec 7.2): the submission once there is one, the
 * decision once made, what the viewer can do about it and, for a PASSED revision, whether it can still be used. Any
 * reader of the lead sees it (spec 4.2). The decision's text is private: never logged.
 */
public record ChecklistReviewView(SubmissionView submissionOrNull, ChecklistDecision decisionOrNull,
                                  ReviewAccess review, PassedView passedOrNull) {

    @Override
    public String toString() {
        return "ChecklistReviewView[hidden]";
    }
}
