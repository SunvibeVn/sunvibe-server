package vn.sunvibe.survey.core.domain.model.checklist;

import vn.sunvibe.survey.core.domain.model.appointment.Basis;

import java.time.Instant;

/**
 * An insert-only takeover row (survey-checklist spec 4.5, 5.2): #1 START_BY_OTHER for an intake declaration (no
 * previous holder, no checkpoint), or TAKEOVER from the previous holder with the checkpoint of the draft just before.
 * The reason, basis and checkpoint are private: never printed.
 */
public record ChecklistTakeover(long id, SurveyChecklistId checklistId, int takeoverNo, TakeoverKind kind,
                                String fromUserId, String toUserId, String reason, Basis basis,
                                ChecklistSnapshot snapshotOrNull, Instant occurredAt) {

    public ChecklistTakeover {
        if (checklistId == null || kind == null || toUserId == null || reason == null || basis == null
                || occurredAt == null || takeoverNo < 1) {
            throw new IllegalArgumentException("A takeover needs its checklist, number, kind, holder, reason, "
                    + "basis and time.");
        }
        if ((kind == TakeoverKind.START_BY_OTHER) != (fromUserId == null)) {
            throw new IllegalArgumentException("Only an intake declaration has no previous holder.");
        }
        if (kind == TakeoverKind.START_BY_OTHER && snapshotOrNull != null) {
            throw new IllegalArgumentException("An intake declaration has no checkpoint.");
        }
    }

    /** Takeover #1 of a draft started by someone who did not perform the visit (spec 4.5). */
    public static ChecklistTakeover startByOther(long id, SurveyChecklistId checklistId, String starterUserId,
                                                 String reason, Basis basis, Instant now) {
        return new ChecklistTakeover(id, checklistId, 1, TakeoverKind.START_BY_OTHER, null, starterUserId, reason,
                basis, null, now);
    }

    @Override
    public String toString() {
        return "ChecklistTakeover[" + checklistId + " #" + takeoverNo + " " + kind + "]";
    }
}
