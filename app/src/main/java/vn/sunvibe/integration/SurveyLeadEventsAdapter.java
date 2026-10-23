package vn.sunvibe.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyPort;
import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyStop;
import vn.sunvibe.acquisition.core.application.shared.external.survey.SurveyAppointmentRef;
import vn.sunvibe.survey.core.application.usecase.AppointmentRef;
import vn.sunvibe.survey.core.application.usecase.LeadStopReason;
import vn.sunvibe.survey.core.application.usecase.LeadSurveyEventsUseCase;

import java.time.Instant;
import java.util.Optional;

/**
 * Cross-module composition (survey-schedule spec 5.4; ADR-009): passes acquisition-service's lead hooks to
 * survey-service's published {@link LeadSurveyEventsUseCase}, in the caller's transaction, and translates the stop
 * reason and the appointment reference between the two modules' types.
 */
@Component
@RequiredArgsConstructor
public class SurveyLeadEventsAdapter implements LeadSurveyPort {

    private final LeadSurveyEventsUseCase leadSurveyEventsUseCase;

    @Override
    public Optional<SurveyAppointmentRef> onReassigned(String leadId, String newAssigneeUserId, String actorUserId,
                                                       Instant at) {
        return leadSurveyEventsUseCase.onReassigned(leadId, newAssigneeUserId, actorUserId, at)
                .map(SurveyLeadEventsAdapter::toRef);
    }

    @Override
    public Optional<SurveyAppointmentRef> onHeldOrClosed(String leadId, LeadSurveyStop stop, String actorUserId,
                                                         String textOrNull, Instant at) {
        LeadStopReason reason = switch (stop) {
            case LEAD_ON_HOLD -> LeadStopReason.LEAD_ON_HOLD;
            case LEAD_CLOSED -> LeadStopReason.LEAD_CLOSED;
        };
        return leadSurveyEventsUseCase.onHeldOrClosed(leadId, reason, actorUserId, textOrNull, at)
                .map(SurveyLeadEventsAdapter::toRef);
    }

    @Override
    public Optional<SurveyAppointmentRef> onIntentChanged(String leadId, int newIntentCycle, String actorUserId,
                                                          Instant at) {
        return leadSurveyEventsUseCase.onIntentChanged(leadId, newIntentCycle, actorUserId, at)
                .map(SurveyLeadEventsAdapter::toRef);
    }

    private static SurveyAppointmentRef toRef(AppointmentRef ref) {
        return new SurveyAppointmentRef(ref.appointmentId(), ref.appointmentNo());
    }
}
