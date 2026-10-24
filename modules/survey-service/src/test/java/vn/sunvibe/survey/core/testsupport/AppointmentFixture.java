package vn.sunvibe.survey.core.testsupport;

import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;

import java.math.BigDecimal;
import java.time.Instant;

/** Builds survey appointments for the domain and service tests. */
public final class AppointmentFixture {

    public static final String LEAD_ID = "01JLEADAAAAAAAAAAAAAAAAAAA";
    public static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
    public static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
    public static final String LEADER = "01JLEADERAAAAAAAAAAAAAAAAA";
    /** Booking time: 2026-10-23 10:05:30 Vietnam time. */
    public static final Instant BOOKED_AT = Instant.parse("2026-10-23T03:05:30Z");
    /** Start: 2026-10-24 10:00 Vietnam time. */
    public static final Instant START = Instant.parse("2026-10-24T03:00:00Z");

    private AppointmentFixture() {
    }

    public static LeadLocationSnapshot leadPlace() {
        return new LeadLocationSnapshot("12 Bach Dang", "48", "Da Nang", "20194", "Hai Chau",
                new Coordinates(new BigDecimal("16.054407"), new BigDecimal("108.202167")));
    }

    public static MeetingLocation atLead() {
        return MeetingLocation.fromLead(leadPlace());
    }

    public static MeetingLocation elsewhere() {
        return MeetingLocation.of("K12/5 Le Duan", "48", "Da Nang", "20195", "Thanh Khe", null);
    }

    public static AppointmentDetails details(Instant start, int duration, MeetingLocation place) {
        return AppointmentDetails.of(start, duration, place, null, null, null);
    }

    /** A first survey at the lead's place, booked by SALE_A for SALE_A, not confirmed. */
    public static SurveyAppointment booked() {
        return booked(false);
    }

    public static SurveyAppointment booked(boolean ownerConfirmed) {
        return SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_ID, 1, AppointmentKind.FIRST, null, null,
                SurveyIntent.LONG_TERM_RENT, 1, leadPlace(), details(START, 60, atLead()), null, SALE_A,
                ownerConfirmed, ownerConfirmed ? "Owner agreed by phone" : null, SALE_A, BOOKED_AT);
    }

    /** A first survey of the intent, visited at the start by SALE_A, who records it. */
    public static SurveyAppointment visited(SurveyIntent intent) {
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_ID, 1,
                AppointmentKind.FIRST, null, null, intent, 1, leadPlace(), details(START, 60, atLead()), null, SALE_A,
                false, null, SALE_A, BOOKED_AT);
        appointment.recordVisit(START, "Đã đến xem nhà", SALE_A, SALE_A, VisitRecordKind.OWN, null, null,
                START.plusSeconds(3600));
        return appointment;
    }
}
