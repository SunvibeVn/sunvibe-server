package vn.sunvibe.survey.core.application.shared.external.lead;

import java.time.Instant;

/**
 * A lead move asked by a survey command: event, wanted status, actor, what it concerns (an appointment or a checklist)
 * and when. A review also gives its outcome and kind as the detail and, when it closes the lead, the fixed close
 * reason, never the reviewer's text (survey-checklist submission spec 4.5, 4.9, A-11).
 */
public record LeadMove(LeadMoveEvent event, LeadMoveTarget target, String actorUserId, String referenceId,
                       int referenceNo, Instant occurredAt, String detailOrNull, String closeReasonOrNull) {

    /** A move with no detail and no close reason: every move but a review. */
    public LeadMove(LeadMoveEvent event, LeadMoveTarget target, String actorUserId, String referenceId,
                    int referenceNo, Instant occurredAt) {
        this(event, target, actorUserId, referenceId, referenceNo, occurredAt, null, null);
    }
}
