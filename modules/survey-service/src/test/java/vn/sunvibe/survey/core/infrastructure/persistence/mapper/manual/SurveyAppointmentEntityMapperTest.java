package vn.sunvibe.survey.core.infrastructure.persistence.mapper.manual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;
import vn.sunvibe.survey.core.domain.model.appointment.Cancellation;
import vn.sunvibe.survey.core.domain.model.appointment.Confirmation;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationState;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecord;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;

/**
 * A stored survey appointment keeps every value in its own field through the row and back (cleanup spec A-5, SV-169):
 * each parameter of {@link SurveyAppointment#load} gets a value no other parameter has, so two arguments swapped in the
 * mapper or in {@code load} fail here. A visit and a cancellation each belong to one status, so the cancellation makes a
 * second trip. Revisions and events are rows of the history adapter; this mapper passes them through.
 */
class SurveyAppointmentEntityMapperTest {

    private static final String ID = "01JA0000000000000000000001";
    private static final String PREVIOUS_ID = "01JA0000000000000000000002";

    private final SurveyAppointmentEntityMapper mapper = new SurveyAppointmentEntityMapper();

    @Test
    void load_everyFieldDistinct_survivesTheMapperRoundTrip() {
        SurveyAppointment back = roundTrip(appointment(AppointmentStatus.VISITED, visit(), null));

        assertEquals(ID, back.getId().getValue());
        assertEquals("lead-1", back.getLeadId());
        assertEquals(2, back.getAppointmentNo());
        assertEquals(AppointmentKind.RESURVEY, back.getKind());
        assertEquals(PREVIOUS_ID, back.getPreviousVisitId().getValue());
        assertEquals("resurvey-1", back.getResurveyReason());
        assertEquals(SurveyIntent.LONG_TERM_RENT, back.getIntent());
        assertEquals(3, back.getIntentCycle());
        assertEquals(AppointmentStatus.VISITED, back.getStatus());
        assertEquals(new AppointmentDetails(at(1), 45, new MeetingLocation("meeting-1", "48", "province-1", "20194",
                "ward-1", new Coordinates(new BigDecimal("16.050001"), new BigDecimal("108.200001"))), "access-1",
                "contact-1", "role-1"), back.getDetails());
        assertEquals(4, back.getCurrentRevisionNo());
        assertEquals(new LeadLocationSnapshot("lead-address-1", "49", "province-2", "20195", "ward-2",
                new Coordinates(new BigDecimal("16.060002"), new BigDecimal("108.210002"))), back.getLeadLocation());
        assertEquals("location-1", back.getLocationReason());
        assertEquals("surveyor-1", back.getSurveyorUserId());
        assertEquals(new Confirmation(ConfirmationState.UNCONFIRMED, ConfirmationCause.TIME_CHANGED, true,
                "confirmer-1", at(2), "confirm-1"), back.getConfirmation());
        assertEquals(new VisitRecord(VisitRecordKind.PROXY, "performer-1", at(5), 5, "visit-1", "recorder-1", at(6),
                "proxy-1", new Basis(LocalDate.of(2026, 11, 4), "basis-1")), back.getVisit());
        assertEquals("creator-1", back.getCreatedBy());
        assertEquals(at(3), back.getCreatedAt());
        assertEquals(at(4), back.getUpdatedAt());
        assertEquals(6L, back.getVersion());

        Cancellation cancellation = new Cancellation(CancelReason.NO_ACCESS, "cancel-1", "canceller-1", at(7));
        assertEquals(new Cancellation(CancelReason.NO_ACCESS, "cancel-1", "canceller-1", at(7)),
                roundTrip(appointment(AppointmentStatus.CANCELLED, null, cancellation)).getCancellation());
    }

    private static SurveyAppointment appointment(AppointmentStatus status, VisitRecord visit,
                                                 Cancellation cancellation) {
        return SurveyAppointment.load(SurveyAppointmentId.of(ID), "lead-1", 2, AppointmentKind.RESURVEY,
                SurveyAppointmentId.of(PREVIOUS_ID), "resurvey-1", SurveyIntent.LONG_TERM_RENT, 3, status,
                new AppointmentDetails(at(1), 45, new MeetingLocation("meeting-1", "48", "province-1", "20194",
                        "ward-1", new Coordinates(new BigDecimal("16.050001"), new BigDecimal("108.200001"))),
                        "access-1", "contact-1", "role-1"),
                4,
                new LeadLocationSnapshot("lead-address-1", "49", "province-2", "20195", "ward-2",
                        new Coordinates(new BigDecimal("16.060002"), new BigDecimal("108.210002"))),
                "location-1", "surveyor-1",
                new Confirmation(ConfirmationState.UNCONFIRMED, ConfirmationCause.TIME_CHANGED, true, "confirmer-1",
                        at(2), "confirm-1"),
                visit, cancellation, "creator-1", at(3), at(4), 6L, List.of(), List.of());
    }

    private static VisitRecord visit() {
        return new VisitRecord(VisitRecordKind.PROXY, "performer-1", at(5), 5, "visit-1", "recorder-1", at(6),
                "proxy-1", new Basis(LocalDate.of(2026, 11, 4), "basis-1"));
    }

    private SurveyAppointment roundTrip(SurveyAppointment appointment) {
        return mapper.toDomain(mapper.toEntity(appointment), appointment.getRevisions(), appointment.getEvents());
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-11-01T01:00:00Z").plusSeconds(60L * minute);
    }
}
