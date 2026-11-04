package vn.sunvibe.survey.core.domain.model.checklist;

import java.time.Instant;
import java.util.List;

/**
 * The decision on one revision (survey-checklist submission spec 4.5, 5.3): outcome and kinds, the reviewer's text, who
 * and when, the lead status around the move and the photo marks; insert-only (I-2). The reason, notes, limitations and
 * marks are private: never in History, a close reason, logs or list views (survey BR-4, BR-14).
 */
public record ChecklistDecision(SurveyChecklistId checklistId, DecisionOutcome outcome,
                                NeedsChangesKind needsChangesKindOrNull, FailedKind failedKindOrNull,
                                String reasonOrNull, String notesOrNull, String limitationsOrNull,
                                String reviewerUserId, Instant decidedAt, String leadStatusBefore,
                                String leadStatusAfter, List<EvidenceRejection> rejections) {

    public ChecklistDecision {
        if (checklistId == null || outcome == null || reviewerUserId == null || decidedAt == null
                || leadStatusBefore == null || leadStatusAfter == null) {
            throw new IllegalArgumentException("A decision needs its revision, outcome, reviewer, time and lead moves.");
        }
        if ((outcome == DecisionOutcome.NEEDS_CHANGES) != (needsChangesKindOrNull != null)
                || (outcome == DecisionOutcome.FAILED) != (failedKindOrNull != null)) {
            throw new IllegalArgumentException("A decision's kinds follow its outcome.");
        }
        rejections = rejections == null ? List.of() : List.copyOf(rejections);
    }

    @Override
    public String toString() {
        return "ChecklistDecision[" + checklistId + " " + outcome + "]";
    }
}
