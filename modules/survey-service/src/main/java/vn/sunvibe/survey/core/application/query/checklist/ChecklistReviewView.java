package vn.sunvibe.survey.core.application.query.checklist;

import vn.sunvibe.survey.core.application.query.common.PassedView;
import vn.sunvibe.survey.core.application.query.common.RefusedPhotoView;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewAccess;

import java.util.List;

/**
 * The review part of a checklist read (survey-checklist submission spec 7.2): the submission once there is one, the
 * decision once made, what the viewer can do about it and, for a PASSED revision, whether it can still be used and why
 * it was withdrawn (property-create requalification spec 4). Any reader of the lead sees it (spec 4.2). The texts of the
 * decision and of the mark are private: never logged. {@code refusedNotCarried} lists the photos of the previous
 * revision's frozen set that a decision on an earlier revision of the visit did not accept and that this revision does
 * not hold, one per photo with its newest refusal (survey-checklist SV-310).
 */
public record ChecklistReviewView(SubmissionView submissionOrNull, ChecklistDecision decisionOrNull,
                                  ReviewAccess review, PassedView passedOrNull,
                                  ChecklistRequalification requalificationOrNull,
                                  List<RefusedPhotoView> refusedNotCarried) {

    public ChecklistReviewView {
        refusedNotCarried = List.copyOf(refusedNotCarried);
    }

    @Override
    public String toString() {
        return "ChecklistReviewView[hidden]";
    }
}
