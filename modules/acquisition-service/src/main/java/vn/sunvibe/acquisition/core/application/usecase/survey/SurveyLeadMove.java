package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/**
 * A lead move asked by a survey command: the event, the status it asks for, who did it, what it concerns (an appointment
 * or a checklist, its id and number within the lead) and when, which is also the time of the lead History entry. A
 * review also gives its outcome and kind as the detail and, when it closes the lead, the close reason: never the
 * reviewer's own text (survey-checklist submission spec 4.5, 4.9, A-11).
 */
public record SurveyLeadMove(SurveyLeadEvent event, SurveyLeadTarget target, String actorUserId, String referenceId,
                             int referenceNo, Instant occurredAt, String detailOrNull, String closeReasonOrNull) {

    public SurveyLeadMove {
        if (event == null || target == null || actorUserId == null || referenceId == null || occurredAt == null) {
            throw new IllegalArgumentException("Every part of a survey lead move is required.");
        }
    }

    /** A move with no detail and no close reason: every move but a review. */
    public SurveyLeadMove(SurveyLeadEvent event, SurveyLeadTarget target, String actorUserId, String referenceId,
                          int referenceNo, Instant occurredAt) {
        this(event, target, actorUserId, referenceId, referenceNo, occurredAt, null, null);
    }
}
