package vn.sunvibe.survey.core.domain.policy;

import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * The current visit and the kind of the next booking (survey-schedule BR-10, BR-12, spec A-9, 4.5). The current visit
 * is the VISITED appointment with the highest number in the lead's current intent cycle; visits of earlier cycles never
 * count again (survey-schedule US-5 AC-4, E-6). Until survey-checklist exists every current visit can still be
 * completed.
 */
public final class CurrentVisitPolicy {

    /** The kind of a booking and, for a re-survey, the visit it follows (null for a first survey). */
    public record BookingKind(AppointmentKind kind, SurveyAppointmentId previousVisitId) {
    }

    private CurrentVisitPolicy() {
    }

    public static Optional<SurveyAppointment> currentVisit(Collection<SurveyAppointment> ofLead, int currentCycle) {
        return ofLead.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.VISITED)
                .filter(appointment -> appointment.getIntentCycle() == currentCycle)
                .max(Comparator.comparingInt(SurveyAppointment::getAppointmentNo));
    }

    /** survey-schedule US-3 AC-2: RESURVEY, linking the current visit, when there is one; FIRST otherwise. */
    public static BookingKind bookingKind(Collection<SurveyAppointment> ofLead, int currentCycle) {
        return currentVisit(ofLead, currentCycle)
                .map(visit -> new BookingKind(AppointmentKind.RESURVEY, visit.getId()))
                .orElse(new BookingKind(AppointmentKind.FIRST, null));
    }
}
