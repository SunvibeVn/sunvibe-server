package vn.sunvibe.survey.core.application.usecase;

import java.time.Instant;
import java.util.Optional;

/**
 * Use Case - LeadSurveyEventsUseCase
 * <p>
 * Published to acquisition-service (survey-schedule spec 5.4, ADR-009): a lead command that reassigns, holds, closes
 * or changes the requirement of a lead tells survey here, in its own transaction and after its lead row was saved and
 * flushed (lock order of spec 5.5). Each call changes the lead's scheduled appointment, if any, and answers it so the
 * lead's History entry can name it. It writes appointment rows only: it never reads or moves the lead and writes no
 * History row (the lead command writes its own, survey-schedule BR-8).
 */
public interface LeadSurveyEventsUseCase {

    /**
     * The lead got another assignee: its scheduled appointment follows with the new surveyor and needs the owner's
     * confirmation again (survey-schedule US-2 AC-3). Empty when nothing is scheduled or the surveyor is already them.
     */
    Optional<AppointmentRef> onReassigned(String leadId, String newAssigneeUserId, String actorUserId, Instant at);

    /**
     * The lead was put on hold or closed: its scheduled appointment is cancelled with that reason, keeping the hold
     * note or the close reason (survey-schedule BR-3). Empty when nothing is scheduled.
     */
    Optional<AppointmentRef> onHeldOrClosed(String leadId, LeadStopReason reason, String actorUserId,
                                            String textOrNull, Instant at);

    /**
     * The lead's requirement changed and it entered the intent cycle {@code newIntentCycle}: its scheduled appointment,
     * booked in an earlier cycle, is cancelled (survey-schedule US-5 AC-4). Empty when nothing is scheduled.
     */
    Optional<AppointmentRef> onIntentChanged(String leadId, int newIntentCycle, String actorUserId, Instant at);
}
