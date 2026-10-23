package vn.sunvibe.survey.core.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sunvibe.survey.core.application.usecase.AppointmentRef;
import vn.sunvibe.survey.core.application.usecase.LeadStopReason;
import vn.sunvibe.survey.core.application.usecase.LeadSurveyEventsUseCase;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Use Case Implementation - LeadSurveyEventsService
 * <p>
 * Applies a lead command to the lead's scheduled appointment, in the transaction of that command. It depends on the
 * appointment repository only, so it can never call the lead back (survey-schedule spec 5.4: no bean cycle, no
 * History row from here).
 */
@Service
@Transactional
public class LeadSurveyEventsService implements LeadSurveyEventsUseCase {

    private final SurveyAppointmentDomainRepository appointments;

    public LeadSurveyEventsService(SurveyAppointmentDomainRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    public Optional<AppointmentRef> onReassigned(String leadId, String newAssigneeUserId, String actorUserId,
                                                 Instant at) {
        return appointments.findScheduledByLeadId(leadId)
                .filter(appointment -> !newAssigneeUserId.equals(appointment.getSurveyorUserId()))
                .map(appointment -> {
                    appointment.reassign(newAssigneeUserId, actorUserId, at);
                    return save(appointment);
                });
    }

    @Override
    public Optional<AppointmentRef> onHeldOrClosed(String leadId, LeadStopReason reason, String actorUserId,
                                                   String textOrNull, Instant at) {
        CancelReason cancelReason = reason == LeadStopReason.LEAD_ON_HOLD
                ? CancelReason.LEAD_ON_HOLD : CancelReason.LEAD_CLOSED;
        return appointments.findScheduledByLeadId(leadId).map(appointment -> {
            appointment.cancelBySystem(cancelReason, textOrNull, actorUserId, at);
            return save(appointment);
        });
    }

    @Override
    public Optional<AppointmentRef> onIntentChanged(String leadId, int newIntentCycle, String actorUserId,
                                                    Instant at) {
        return appointments.findScheduledByLeadId(leadId).map(appointment -> {
            // The lead moved to a later cycle; an appointment already in that cycle means the two modules disagree
            if (appointment.getIntentCycle() >= newIntentCycle) {
                throw new IllegalStateException("The scheduled appointment is not of an earlier intent cycle.");
            }
            appointment.cancelBySystem(CancelReason.INTENT_CHANGED, null, actorUserId, at);
            return save(appointment);
        });
    }

    private AppointmentRef save(SurveyAppointment appointment) {
        appointments.save(appointment);
        return new AppointmentRef(appointment.getId().getValue(), appointment.getAppointmentNo());
    }
}
