package vn.sunvibe.survey.core.domain.policy;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.*;

/** The current visit (survey-schedule spec A-9) and the booking kind (spec 4.5, US-3 AC-2, US-5 AC-4, E-6). */
class CurrentVisitPolicyTest {

    @Test
    void currentVisit_isTheLatestVisitedByNumberInTheCurrentCycle() {
        SurveyAppointment first = visited(1, 1);
        SurveyAppointment second = visited(2, 1);
        SurveyAppointment cancelled = appointment(3, 1);
        cancelled.cancel(CancelReason.STAFF_CANCELLED, null, SALE_A, START.plusSeconds(60));

        assertEquals(Optional.of(second),
                CurrentVisitPolicy.currentVisit(List.of(second, first, cancelled), 1));
        assertEquals(Optional.empty(), CurrentVisitPolicy.currentVisit(List.of(cancelled), 1));
    }

    @Test
    void currentVisit_noneAfterACycleChange() {
        assertEquals(Optional.empty(), CurrentVisitPolicy.currentVisit(List.of(visited(1, 1), visited(2, 2)), 3));
    }

    @Test
    void bookingKind_resurveyLinksTheLatestVisit_firstAfterACycleChange() {
        SurveyAppointment visit = visited(2, 1);

        CurrentVisitPolicy.BookingKind resurvey = CurrentVisitPolicy.bookingKind(List.of(visited(1, 1), visit), 1);
        CurrentVisitPolicy.BookingKind first = CurrentVisitPolicy.bookingKind(List.of(visited(1, 1), visit), 2);

        assertEquals(AppointmentKind.RESURVEY, resurvey.kind());
        assertEquals(visit.getId(), resurvey.previousVisitId());
        assertEquals(AppointmentKind.FIRST, first.kind());
        assertEquals(null, first.previousVisitId());
    }

    private static SurveyAppointment appointment(int no, int cycle) {
        return SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_ID, no, AppointmentKind.FIRST, null, null,
                SurveyIntent.LONG_TERM_RENT, cycle, leadPlace(), details(START, 60, atLead()), null, SALE_A, false,
                null, SALE_A, BOOKED_AT);
    }

    private static SurveyAppointment visited(int no, int cycle) {
        SurveyAppointment appointment = appointment(no, cycle);
        appointment.recordVisit(START, "Visited", SALE_A, SALE_A, VisitRecordKind.OWN, null, null,
                START.plusSeconds(600));
        return appointment;
    }
}
