package vn.sunvibe.survey.core.domain.model.appointment;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.*;

/**
 * The value types of an appointment: details with their limits (survey-schedule BR-4), the meeting place and when it
 * differs from the lead (spec A-16), coordinates, the basis of a proxy record or resumption (BR-9, A-25), cancel
 * reasons and the appointment id.
 */
class AppointmentValuesTest {

    @Test
    void details_ofValidValues_cleansTheTexts() {
        AppointmentDetails details = AppointmentDetails.of(START, 60, atLead(), "  Ring twice ", " ", "Guard");

        assertEquals("Ring twice", details.accessNote());
        assertNull(details.onSiteContactName());
        assertEquals("Guard", details.onSiteContactRole());
    }

    @Test
    void details_brokenValues_nameTheirField() {
        assertField("startAt", () -> AppointmentDetails.of(START.plusSeconds(30), 60, atLead(), null, null, null));
        assertField("durationMinutes", () -> AppointmentDetails.of(START, 50, atLead(), null, null, null));
        assertField("accessNote", () -> AppointmentDetails.of(START, 60, atLead(), "x".repeat(1001), null, null));
        assertField("accessNote", () -> AppointmentDetails.of(START, 60, atLead(), "0905 123 456", null, null));
        assertField("onSiteContactName", () -> AppointmentDetails.of(START, 60, atLead(), null, "x".repeat(101),
                null));
        assertField("onSiteContactRole", () -> AppointmentDetails.of(START, 60, atLead(), null, null,
                "+84 905 123 456"));
        assertThrows(IllegalArgumentException.class, () -> AppointmentDetails.of(START, 60, null, null, null, null));
    }

    @Test
    void meetingLocation_address_isCheckedLikeStaffText() {
        assertField("meetingLocation.addressText", () -> MeetingLocation.of(" ", "48", "Da Nang", "20194", "Hai Chau",
                null));
        assertField("meetingLocation.addressText", () -> MeetingLocation.of("x".repeat(256), "48", "Da Nang",
                "20194", "Hai Chau", null));
        assertField("meetingLocation.addressText", () -> MeetingLocation.of("12 Bach Dang, 0905123456", "48",
                "Da Nang", "20194", "Hai Chau", null));
    }

    @Test
    void meetingLocation_differsFromTheLead_byAddressWardOrPin() {
        assertFalse(atLead().differsFrom(leadPlace()));
        assertFalse(MeetingLocation.of("  12  bach DANG ", "48", "Da Nang", "20194", "Hai Chau",
                leadPlace().coordinates()).differsFrom(leadPlace()), "case and spaces do not count");
        assertTrue(MeetingLocation.of("14 Bach Dang", "48", "Da Nang", "20194", "Hai Chau", leadPlace().coordinates())
                .differsFrom(leadPlace()));
        assertTrue(MeetingLocation.of("12 Bach Dang", "48", "Da Nang", "20195", "Thanh Khe",
                leadPlace().coordinates()).differsFrom(leadPlace()));
        assertTrue(MeetingLocation.of("12 Bach Dang", "48", "Da Nang", "20194", "Hai Chau", null)
                .differsFrom(leadPlace()));
    }

    @Test
    void coordinates_areComparedAtSixDecimals() {
        assertEquals(new Coordinates(new BigDecimal("16.05"), new BigDecimal("108.2")),
                new Coordinates(new BigDecimal("16.050000"), new BigDecimal("108.200000")));
        assertThrows(IllegalArgumentException.class, () -> new Coordinates(null, BigDecimal.ONE));
    }

    @Test
    void basis_needsADateAndText() {
        Basis basis = Basis.of("basis", LocalDate.parse("2026-10-20"), "  Owner said so ");
        assertEquals("Owner said so", basis.text());
        assertField("basis.confirmedOn", () -> Basis.of("basis", null, "text"));
        assertField("proxy.basis.text", () -> Basis.of("proxy.basis", LocalDate.parse("2026-10-20"), " "));
        assertField("basis.text", () -> Basis.of("basis", LocalDate.parse("2026-10-20"), "0905 123 456"));
    }

    @Test
    void basis_requireWithin_refusesDatesAfterTodayOrBeforeTheEvent() {
        Basis basis = Basis.of("basis", LocalDate.parse("2026-10-20"), "Owner said so");
        LocalDate today = LocalDate.parse("2026-10-23");

        assertDoesNotThrow(() -> basis.requireWithin("basis", LocalDate.parse("2026-10-20"), today));
        assertField("basis.confirmedOn", () -> basis.requireWithin("basis", LocalDate.parse("2026-10-21"), today));
        assertField("basis.confirmedOn", () -> basis.requireWithin("basis", null, LocalDate.parse("2026-10-19")));
    }

    @Test
    void cancelReason_userChoices_areTheFiveOfTheForm() {
        assertTrue(CancelReason.OWNER_CANCELLED.isUserChoice());
        assertTrue(CancelReason.STAFF_CANCELLED.isUserChoice());
        assertTrue(CancelReason.OWNER_ABSENT.isUserChoice());
        assertTrue(CancelReason.NO_ACCESS.isUserChoice());
        assertTrue(CancelReason.OTHER.isUserChoice());
        assertFalse(CancelReason.LEAD_ON_HOLD.isUserChoice());
        assertFalse(CancelReason.LEAD_CLOSED.isUserChoice());
        assertFalse(CancelReason.INTENT_CHANGED.isUserChoice());
    }

    @Test
    void appointmentId_isAUlid() {
        SurveyAppointmentId id = SurveyAppointmentId.generate();
        assertEquals(26, id.getValue().length());
        assertEquals(id, SurveyAppointmentId.of(id.getValue()));
        assertTrue(SurveyAppointmentId.isValid(id.getValue()));
        assertFalse(SurveyAppointmentId.isValid("not-a-ulid"));
        assertFalse(SurveyAppointmentId.isValid(null));
        assertThrows(IllegalArgumentException.class, () -> SurveyAppointmentId.of("x"));
    }

    @Test
    void instantsOfTheFixture_areWholeMinutes() {
        assertEquals(0, START.getEpochSecond() % 60);
        assertTrue(Instant.parse("2026-10-24T03:00:00Z").equals(START));
    }

    private static void assertField(String field, org.junit.jupiter.api.function.Executable executable) {
        assertEquals(field, assertThrows(InvalidSurveyValueException.class, executable).getField());
    }
}
