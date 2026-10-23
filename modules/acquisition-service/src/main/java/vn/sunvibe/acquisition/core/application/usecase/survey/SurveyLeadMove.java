package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.time.Instant;

/**
 * A lead move asked by a survey command: the event, the status it asks for, who did it, the appointment it concerns
 * (id and number within the lead) and when, which is also the time of the lead History entry.
 */
public record SurveyLeadMove(SurveyLeadEvent event, SurveyLeadTarget target, String actorUserId, String referenceId,
                             int referenceNo, Instant occurredAt) {

    public SurveyLeadMove {
        if (event == null || target == null || actorUserId == null || referenceId == null || occurredAt == null) {
            throw new IllegalArgumentException("Every part of a survey lead move is required.");
        }
    }
}
