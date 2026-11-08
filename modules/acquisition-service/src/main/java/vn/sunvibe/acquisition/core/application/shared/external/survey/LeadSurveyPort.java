package vn.sunvibe.acquisition.core.application.shared.external.survey;

import java.time.Instant;
import java.util.Optional;

/**
 * Port to the survey appointments of a lead, owned by survey-service (survey-schedule spec 5.4, ADR-009). The lead
 * commands call it in the same transaction, after the lead row was saved and flushed (lock order of survey-schedule spec 5.5); it
 * writes appointment rows only and never calls the lead back. Each hook answers the scheduled appointment it
 * changed, if any, so the lead's History entry can name it.
 */
public interface LeadSurveyPort {

    /**
     * The survey record of the lead's intent cycle (survey-checklist submission spec 4.7, 4.8, plan PD-3). Asked after
     * the lead row is locked by the command; acquisition passes the cycle, so survey never calls the lead back.
     */
    SurveyState surveyState(String leadId, int intentCycle);


    /** The lead got another assignee: its scheduled appointment follows (survey-schedule US-2 AC-3). */
    Optional<SurveyAppointmentRef> onReassigned(String leadId, String newAssigneeUserId, String actorUserId,
                                                Instant at);

    /**
     * The lead was put on hold or closed: its scheduled appointment is cancelled with the hold note or the close
     * reason (survey-schedule BR-3).
     */
    Optional<SurveyAppointmentRef> onHeldOrClosed(String leadId, LeadSurveyStop stop, String actorUserId,
                                                  String textOrNull, Instant at);

    /** The lead's requirement changed: its scheduled appointment is cancelled (survey-schedule US-5 AC-4). */
    Optional<SurveyAppointmentRef> onIntentChanged(String leadId, int newIntentCycle, String actorUserId, Instant at);
}
