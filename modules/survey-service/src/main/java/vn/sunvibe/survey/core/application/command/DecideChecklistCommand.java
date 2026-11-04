package vn.sunvibe.survey.core.application.command;

import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;

import java.util.List;

/**
 * A reviewer's decision as sent (survey-checklist submission spec 4.5, 7.1, A-5, A-10). The texts are private: never
 * printed.
 */
public record DecideChecklistCommand(String checklistId, Long expectedVersion, DecisionOutcome outcome,
                                     NeedsChangesKind needsChangesKindOrNull, FailedKind failedKindOrNull,
                                     String reasonOrNull, String notesOrNull, String limitationsOrNull,
                                     List<RejectedEvidence> rejectedEvidence) {

    /** A frozen photo the reviewer does not accept, as sent. */
    public record RejectedEvidence(String mediaId, String reason) {

        @Override
        public String toString() {
            return "RejectedEvidence[" + mediaId + "]";
        }
    }

    @Override
    public String toString() {
        return "DecideChecklistCommand[" + checklistId + " " + outcome + "]";
    }
}
