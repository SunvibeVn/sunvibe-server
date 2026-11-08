package vn.sunvibe.survey.core.presentation.converter;

import vn.sunvibe.survey.core.application.query.checklist.SubmissionView;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistDecision;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistRequalification;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistSubmission;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceEntry;
import vn.sunvibe.survey.core.domain.policy.checklist.ReviewAccess;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistDecisionResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistRequalificationResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistReviewResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistStaffMember;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSubmissionResponse;
import vn.sunvibe.survey.core.presentation.dto.response.checklist.ChecklistSubmittedEvidence;

import java.util.function.Function;

/**
 * Converter - ChecklistReviewConverter
 * <p>
 * The review blocks of the checklist response (survey-checklist submission spec 7.2): the submission with its evidence,
 * the decision, what the viewer may decide, and a requalification mark.
 */
final class ChecklistReviewConverter {

    private ChecklistReviewConverter() {
    }

    static ChecklistSubmissionResponse submission(SubmissionView view, Function<String, String> names) {
        if (view == null) {
            return null;
        }
        ChecklistSubmission submission = view.submission();
        return new ChecklistSubmissionResponse(new ChecklistStaffMember(submission.submittedByUserId(),
                names.apply(submission.submittedByUserId())), submission.submittedAt(), submission.mediaPolicy(),
                submission.contentSha256(), view.evidence().stream().map(evidence -> {
                    EvidenceEntry entry = evidence.entry();
                    return new ChecklistSubmittedEvidence(entry.mediaId(), entry.category(), entry.spaceIdOrNull(),
                            entry.spaceCodeOrNull(), entry.fullSha256(), evidence.acceptedOrNull(),
                            evidence.rejectionReasonOrNull());
                }).toList());
    }

    static ChecklistDecisionResponse decision(ChecklistDecision decision, Function<String, String> names) {
        return decision == null ? null : new ChecklistDecisionResponse(decision.outcome(),
                decision.needsChangesKindOrNull(), decision.failedKindOrNull(), decision.reasonOrNull(),
                decision.notesOrNull(), decision.limitationsOrNull(), new ChecklistStaffMember(
                decision.reviewerUserId(), names.apply(decision.reviewerUserId())), decision.decidedAt());
    }

    static ChecklistRequalificationResponse requalification(ChecklistRequalification mark,
                                                            Function<String, String> names) {
        return mark == null ? null : new ChecklistRequalificationResponse(mark.kind(), mark.reason(), mark.eventSource(),
                mark.scope(), mark.basisDate(), new ChecklistStaffMember(mark.markedByUserId(),
                names.apply(mark.markedByUserId())), mark.markedAt());
    }

    static ChecklistReviewResponse review(ReviewAccess access) {
        return new ChecklistReviewResponse(access.canReview(), access.reasonOrNull(), access.decisionBlockedBy());
    }
}
