package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.application.usecase.AppointmentRef;
import vn.sunvibe.survey.core.application.usecase.LeadStopReason;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEvent;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEventType;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationState;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyAppointmentRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.LEADER;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.LEAD_ID;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.SALE_A;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.SALE_B;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.booked;

class LeadSurveyEventsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T05:00:00Z");
    private static final String OTHER_LEAD = "01JOTHERLEADAAAAAAAAAAAAAA";

    private final List<String> journal = new ArrayList<>();
    private InMemorySurveyAppointmentRepository appointments;
    private LeadSurveyEventsService service;
    private SurveyAppointment scheduled;

    @BeforeEach
    void setUp() {
        appointments = new InMemorySurveyAppointmentRepository(journal);
        service = new LeadSurveyEventsService(appointments);
        scheduled = booked(true);
        appointments.save(scheduled);
        journal.clear();
    }

    @Test
    void onReassigned_scheduledAppointment_followsTheNewAssigneeUnconfirmed() {
        Optional<AppointmentRef> ref = service.onReassigned(LEAD_ID, SALE_B, LEADER, NOW);

        assertEquals(Optional.of(new AppointmentRef(scheduled.getId().getValue(), 1)), ref);
        SurveyAppointment stored = stored();
        assertEquals(SALE_B, stored.getSurveyorUserId());
        assertEquals(ConfirmationState.UNCONFIRMED, stored.getConfirmation().state());
        assertEquals(ConfirmationCause.REASSIGNED, stored.getConfirmation().cause());
        assertTrue(stored.getEvents().stream().map(AppointmentEvent::type)
                .anyMatch(type -> type == AppointmentEventType.SURVEYOR_CHANGED));
        assertEquals(List.of("save:SCHEDULED"), journal);
    }

    @Test
    void onReassigned_surveyorAlreadyTheNewAssignee_answersEmptyAndSavesNothing() {
        assertEquals(Optional.empty(), service.onReassigned(LEAD_ID, SALE_A, LEADER, NOW));

        assertEquals(List.of(), journal);
        assertEquals(SALE_A, stored().getSurveyorUserId());
    }

    @Test
    void onReassigned_noScheduledAppointment_answersEmptyAndSavesNothing() {
        assertEquals(Optional.empty(), service.onReassigned(OTHER_LEAD, SALE_B, LEADER, NOW));

        assertEquals(List.of(), journal);
    }

    @Test
    void onHeldOrClosed_holdWithLongNoteHoldingAPhoneNumber_cancelsKeepingTheNote() {
        String note = "Owner abroad until December, call 0905 123 456 then. " + "x".repeat(1900);

        Optional<AppointmentRef> ref = service.onHeldOrClosed(LEAD_ID, LeadStopReason.LEAD_ON_HOLD, SALE_A, note,
                NOW);

        assertEquals(Optional.of(new AppointmentRef(scheduled.getId().getValue(), 1)), ref);
        SurveyAppointment stored = stored();
        assertEquals(AppointmentStatus.CANCELLED, stored.getStatus());
        assertEquals(CancelReason.LEAD_ON_HOLD, stored.getCancellation().reason());
        assertEquals(note, stored.getCancellation().description());
        assertEquals(SALE_A, stored.getCancellation().cancelledBy());
        assertEquals(NOW, stored.getCancellation().cancelledAt());
    }

    @Test
    void onHeldOrClosed_holdWithoutNote_cancelsWithoutDescription() {
        service.onHeldOrClosed(LEAD_ID, LeadStopReason.LEAD_ON_HOLD, SALE_A, null, NOW);

        assertEquals(CancelReason.LEAD_ON_HOLD, stored().getCancellation().reason());
        assertNull(stored().getCancellation().description());
    }

    @Test
    void onHeldOrClosed_close_cancelsWithLeadClosedKeepingTheReason() {
        service.onHeldOrClosed(LEAD_ID, LeadStopReason.LEAD_CLOSED, LEADER, "Owner sold the house", NOW);

        assertEquals(AppointmentStatus.CANCELLED, stored().getStatus());
        assertEquals(CancelReason.LEAD_CLOSED, stored().getCancellation().reason());
        assertEquals("Owner sold the house", stored().getCancellation().description());
    }

    @Test
    void onHeldOrClosed_noScheduledAppointment_answersEmptyAndSavesNothing() {
        service.onHeldOrClosed(LEAD_ID, LeadStopReason.LEAD_CLOSED, LEADER, "Owner sold the house", NOW);
        journal.clear();

        assertEquals(Optional.empty(),
                service.onHeldOrClosed(LEAD_ID, LeadStopReason.LEAD_ON_HOLD, SALE_A, null, NOW));
        assertEquals(List.of(), journal);
    }

    @Test
    void onIntentChanged_scheduledAppointment_cancelsWithIntentChangedWithoutText() {
        Optional<AppointmentRef> ref = service.onIntentChanged(LEAD_ID, 2, SALE_A, NOW);

        assertEquals(Optional.of(new AppointmentRef(scheduled.getId().getValue(), 1)), ref);
        assertEquals(AppointmentStatus.CANCELLED, stored().getStatus());
        assertEquals(CancelReason.INTENT_CHANGED, stored().getCancellation().reason());
        assertNull(stored().getCancellation().description());
    }

    @Test
    void onIntentChanged_noScheduledAppointment_answersEmptyAndSavesNothing() {
        assertEquals(Optional.empty(), service.onIntentChanged(OTHER_LEAD, 2, SALE_A, NOW));

        assertEquals(List.of(), journal);
    }

    @Test
    void onIntentChanged_scheduledAppointmentOfTheNewCycle_isRefused() {
        assertThrows(IllegalStateException.class, () -> service.onIntentChanged(LEAD_ID, 1, SALE_A, NOW));

        assertEquals(List.of(), journal);
    }

    private SurveyAppointment stored() {
        return appointments.findById(scheduled.getId()).orElseThrow();
    }
}
