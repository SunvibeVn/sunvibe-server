package vn.sunvibe.survey.core.domain.model.appointment;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.exception.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.exception.PerformerMismatchException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.survey.core.testsupport.AppointmentFixture.*;

/**
 * The survey appointment of survey-schedule spec 4.4..4.8: booking, change with revisions, the owner's confirmation
 * (A-6, A-28), cancellation by staff and by the system, reassignment, the visit record with its time and performer
 * rules, and the revision and holder in force at a minute.
 */
class SurveyAppointmentTest {

    private static final Instant LATER = BOOKED_AT.plus(Duration.ofHours(2));

    // ==================== booking ====================

    @Test
    void book_unconfirmed_isScheduledWithRevisionOneHolderAndBookedEvent() {
        SurveyAppointment appointment = booked();

        assertEquals(AppointmentStatus.SCHEDULED, appointment.getStatus());
        assertEquals(1, appointment.getAppointmentNo());
        assertEquals(AppointmentKind.FIRST, appointment.getKind());
        assertEquals(SALE_A, appointment.getSurveyorUserId());
        assertEquals(1, appointment.getCurrentRevisionNo());
        assertEquals(START, appointment.getDetails().startAt());
        assertEquals(BOOKED_AT, appointment.getCreatedAt());
        assertEquals(SALE_A, appointment.getCreatedBy());

        List<AppointmentRevision> revisions = appointment.getRevisions();
        assertEquals(1, revisions.size());
        assertEquals(1, revisions.get(0).revisionNo());
        assertEquals(BOOKED_AT, revisions.get(0).validFrom());
        assertNull(revisions.get(0).reason(), "revision 1 at the lead's place has no reason");

        assertEquals(List.of(new Holder(SALE_A, BOOKED_AT, null)), appointment.holders());
        assertEquals(List.of(AppointmentEventType.BOOKED), types(appointment));

        Confirmation confirmation = appointment.getConfirmation();
        assertEquals(ConfirmationState.UNCONFIRMED, confirmation.state());
        assertEquals(ConfirmationCause.NEW, confirmation.cause());
        assertFalse(confirmation.everConfirmed());
        assertEquals(SALE_A, confirmation.changedBy());
        assertEquals(BOOKED_AT, confirmation.changedAt());
        assertFalse(confirmation.needsReconfirmation());
        assertEquals(2, appointment.getPendingEvents().size() + appointment.getPendingRevisions().size());
    }

    @Test
    void book_ownerConfirmed_isConfirmedWithBookedAndConfirmedEvents() {
        SurveyAppointment appointment = booked(true);

        Confirmation confirmation = appointment.getConfirmation();
        assertEquals(ConfirmationState.CONFIRMED, confirmation.state());
        assertNull(confirmation.cause());
        assertTrue(confirmation.everConfirmed());
        assertEquals("Owner agreed by phone", confirmation.note());
        assertEquals(List.of(AppointmentEventType.BOOKED, AppointmentEventType.CONFIRMED), types(appointment));
    }

    @Test
    void book_confirmationNoteWithoutConfirmation_isRefused() {
        InvalidSurveyValueException ex = assertThrows(InvalidSurveyValueException.class, () ->
                SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_ID, 1, AppointmentKind.FIRST, null, null,
                        SurveyIntent.LONG_TERM_RENT, 1, leadPlace(), details(START, 60, atLead()), null, SALE_A,
                        false, "note", SALE_A, BOOKED_AT));

        assertEquals("confirmationNote", ex.getField());
    }

    @Test
    void book_placeDiffersWithoutReason_isRefusedOnLocationReason() {
        InvalidSurveyValueException ex = assertThrows(InvalidSurveyValueException.class, () -> book(elsewhere(), null));

        assertEquals("locationReason", ex.getField());
    }

    @Test
    void book_placeDiffersWithReason_keepsTheReasonOnRevisionOne() {
        SurveyAppointment appointment = book(elsewhere(), "Owner meets us at the cafe first");

        assertEquals("Owner meets us at the cafe first", appointment.getLocationReason());
        assertEquals("Owner meets us at the cafe first", appointment.getRevisions().get(0).reason());
        assertEquals(leadPlace(), appointment.getLeadLocation());
    }

    @Test
    void book_reasonForThePlaceAtTheLead_isNotStored() {
        SurveyAppointment appointment = book(atLead(), "ignored?");

        assertNull(appointment.getLocationReason());
        assertNull(appointment.getRevisions().get(0).reason());
    }

    @Test
    void book_resurveyWithoutReasonOrLink_isRefused() {
        SurveyAppointmentId visit = SurveyAppointmentId.generate();
        assertEquals("resurveyReason", assertThrows(InvalidSurveyValueException.class, () -> SurveyAppointment.book(
                SurveyAppointmentId.generate(), LEAD_ID, 2, AppointmentKind.RESURVEY, visit, null,
                SurveyIntent.LONG_TERM_RENT, 1, leadPlace(), details(START, 60, atLead()), null, SALE_A, false, null,
                SALE_A, BOOKED_AT)).getField());

        SurveyAppointment resurvey = SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_ID, 2,
                AppointmentKind.RESURVEY, visit, "Owner added a floor", SurveyIntent.LONG_TERM_RENT, 1, leadPlace(),
                details(START, 60, atLead()), null, SALE_A, false, null, SALE_A, BOOKED_AT);
        assertEquals(visit, resurvey.getPreviousVisitId());
        assertEquals("Owner added a floor", resurvey.getResurveyReason());
    }

    @Test
    void book_startOutsideTheWindow_isRefusedOnStartAt() {
        assertEquals("startAt", assertThrows(InvalidSurveyValueException.class, () -> SurveyAppointment.book(
                SurveyAppointmentId.generate(), LEAD_ID, 1, AppointmentKind.FIRST, null, null,
                SurveyIntent.LONG_TERM_RENT, 1, leadPlace(), details(Instant.parse("2026-10-23T03:04:00Z"), 60,
                        atLead()), null, SALE_A, false, null, SALE_A, BOOKED_AT)).getField());
    }

    // ==================== change ====================

    @Test
    void change_time_addsRevisionWithFullValuesAndUnconfirmsWithTimeChanged() {
        SurveyAppointment appointment = booked(true);
        AppointmentDetails moved = AppointmentDetails.of(START.plus(Duration.ofHours(1)), 90, atLead(),
                "Gate code given on site", "Anh Tuan", "Guard");

        appointment.change(moved, "Owner asked for later", LEADER, LATER);

        assertEquals(2, appointment.getCurrentRevisionNo());
        assertEquals(moved, appointment.getDetails());
        AppointmentRevision revision = appointment.getRevisions().get(1);
        assertEquals(2, revision.revisionNo());
        assertEquals(LATER, revision.validFrom());
        assertEquals(moved, revision.details());
        assertEquals("Owner asked for later", revision.reason());
        assertEquals(LEADER, revision.actorUserId());
        assertEquals(START, appointment.getRevisions().get(0).details().startAt(), "earlier values are kept");
        assertEquals(ConfirmationState.UNCONFIRMED, appointment.getConfirmation().state());
        assertEquals(ConfirmationCause.TIME_CHANGED, appointment.getConfirmation().cause());
        assertTrue(appointment.getConfirmation().needsReconfirmation());
        AppointmentEvent last = lastEvent(appointment);
        assertEquals(AppointmentEventType.CHANGED, last.type());
        assertEquals(2, last.revisionNo());
        assertEquals(ConfirmationCause.TIME_CHANGED, last.confirmationCause());
    }

    @Test
    void change_placeOnly_isLocationChanged_andBoth_isTimeChanged() {
        SurveyAppointment place = booked(true);
        place.change(details(START, 60, elsewhere()), "Meet at the side gate", SALE_A, LATER);
        assertEquals(ConfirmationCause.LOCATION_CHANGED, place.getConfirmation().cause());

        SurveyAppointment both = booked(true);
        both.change(details(START.plus(Duration.ofHours(1)), 60, elsewhere()), "Both", SALE_A, LATER);
        assertEquals(ConfirmationCause.TIME_CHANGED, both.getConfirmation().cause());
    }

    @Test
    void change_ofAnUnconfirmedAppointment_replacesTheCauseNew() {
        SurveyAppointment appointment = booked(false);

        appointment.change(details(START, 75, atLead()), "Longer visit", SALE_A, LATER);

        assertEquals(ConfirmationCause.TIME_CHANGED, appointment.getConfirmation().cause());
        assertFalse(appointment.getConfirmation().needsReconfirmation());
    }

    @Test
    void change_accessNoteOnly_keepsTheConfirmation() {
        SurveyAppointment appointment = booked(true);

        appointment.change(AppointmentDetails.of(START, 60, atLead(), "Ring twice", null, null), "Note", SALE_A,
                LATER);

        assertEquals(ConfirmationState.CONFIRMED, appointment.getConfirmation().state());
        assertNull(lastEvent(appointment).confirmationCause());
    }

    @Test
    void change_nothingChanged_isRefusedWithoutField() {
        SurveyAppointment appointment = booked();

        InvalidSurveyValueException ex = assertThrows(InvalidSurveyValueException.class,
                () -> appointment.change(details(START, 60, atLead()), "Same", SALE_A, LATER));

        assertNull(ex.getField());
        assertEquals("Nothing changed.", ex.getMessage());
    }

    @Test
    void change_withoutReason_isRefusedOnReason() {
        assertEquals("reason", assertThrows(InvalidSurveyValueException.class,
                () -> booked().change(details(START, 90, atLead()), "  ", SALE_A, LATER)).getField());
    }

    @Test
    void change_newStartInThePast_isRefusedButKeptStartIsNotRechecked() {
        SurveyAppointment appointment = booked();
        Instant afterStart = START.plus(Duration.ofHours(3));

        Instant beforeLater = Instant.parse("2026-10-23T05:00:00Z");
        assertEquals("startAt", assertThrows(InvalidSurveyValueException.class, () -> appointment.change(
                details(beforeLater, 60, atLead()), "Earlier", SALE_A, LATER)).getField());

        // survey-schedule BR-4: an overdue appointment may still change what is not its start
        appointment.change(AppointmentDetails.of(START, 60, atLead(), "Side door", null, null), "Note", SALE_A,
                afterStart);
        assertEquals(2, appointment.getCurrentRevisionNo());
    }

    // ==================== confirmation ====================

    @Test
    void confirm_twice_isANoOpTheSecondTime() {
        SurveyAppointment appointment = booked();

        assertTrue(appointment.confirm(LEADER, "Owner confirmed again", LATER));
        int events = appointment.getEvents().size();
        assertFalse(appointment.confirm(LEADER, "again", LATER.plusSeconds(60)));

        assertEquals(events, appointment.getEvents().size());
        Confirmation confirmation = appointment.getConfirmation();
        assertEquals(ConfirmationState.CONFIRMED, confirmation.state());
        assertEquals(LEADER, confirmation.changedBy());
        assertEquals(LATER, confirmation.changedAt());
        assertEquals("Owner confirmed again", confirmation.note());
        assertEquals("Owner confirmed again", lastEvent(appointment).note());
    }

    @Test
    void unconfirm_recordsCauseUserAndThenNeedsReconfirmation() {
        SurveyAppointment appointment = booked(true);

        assertTrue(appointment.unconfirm(SALE_A, null, LATER));
        assertFalse(appointment.unconfirm(SALE_A, null, LATER));

        assertEquals(ConfirmationCause.USER, appointment.getConfirmation().cause());
        assertTrue(appointment.getConfirmation().needsReconfirmation());
        assertEquals(AppointmentEventType.UNCONFIRMED, lastEvent(appointment).type());
        assertEquals(ConfirmationCause.USER, lastEvent(appointment).confirmationCause());
    }

    @Test
    void confirm_noteWithPhone_isRefused() {
        assertEquals("note", assertThrows(InvalidSurveyValueException.class,
                () -> booked().confirm(SALE_A, "call 0905 123 456", LATER)).getField());
    }

    // ==================== cancel ====================

    @Test
    void cancel_otherWithoutDescription_isRefused() {
        assertEquals("description", assertThrows(InvalidSurveyValueException.class,
                () -> booked().cancel(CancelReason.OTHER, null, SALE_A, LATER)).getField());
    }

    @Test
    void cancel_userDescriptionOf501_isRefused_andSystemReasonByUser_isRefused() {
        assertEquals("description", assertThrows(InvalidSurveyValueException.class,
                () -> booked().cancel(CancelReason.OWNER_CANCELLED, "x".repeat(501), SALE_A, LATER)).getField());
        assertEquals("cancelReason", assertThrows(InvalidSurveyValueException.class,
                () -> booked().cancel(CancelReason.LEAD_CLOSED, null, SALE_A, LATER)).getField());
    }

    @Test
    void cancel_ownerAbsent_isCancelledWithReasonAndEvent() {
        SurveyAppointment appointment = booked();

        appointment.cancel(CancelReason.OWNER_ABSENT, "Waited 30 minutes", SALE_A, LATER);

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        Cancellation cancellation = appointment.getCancellation();
        assertEquals(CancelReason.OWNER_ABSENT, cancellation.reason());
        assertEquals("Waited 30 minutes", cancellation.description());
        assertEquals(SALE_A, cancellation.cancelledBy());
        assertEquals(LATER, cancellation.cancelledAt());
        assertEquals(AppointmentEventType.CANCELLED, lastEvent(appointment).type());
    }

    @Test
    void cancelBySystem_textOf2000_isAccepted() {
        SurveyAppointment appointment = booked();

        appointment.cancelBySystem(CancelReason.LEAD_ON_HOLD, "x".repeat(2000), LEADER, LATER);

        assertEquals(2000, appointment.getCancellation().description().length());
        assertEquals(CancelReason.LEAD_ON_HOLD, appointment.getCancellation().reason());
    }

    /** Review Focus 2: the hold note is the user's own text (checked by the lead queue), kept as is. */
    @Test
    void cancelBySystem_textWithPhoneNumber_isKept() {
        SurveyAppointment appointment = booked();

        appointment.cancelBySystem(CancelReason.LEAD_ON_HOLD, "  Owner abroad, call 0905 123 456  ", LEADER, LATER);

        assertEquals("Owner abroad, call 0905 123 456", appointment.getCancellation().description());
    }

    @Test
    void cancelBySystem_withoutText_andIntentChanged_isAccepted() {
        SurveyAppointment appointment = booked();

        appointment.cancelBySystem(CancelReason.INTENT_CHANGED, null, SALE_A, LATER);

        assertNull(appointment.getCancellation().description());
        assertThrows(IllegalArgumentException.class,
                () -> booked().cancelBySystem(CancelReason.OTHER, null, SALE_A, LATER));
    }

    // ==================== reassign ====================

    @Test
    void reassign_addsHolderUnconfirmsAndRecordsSurveyorChanged() {
        SurveyAppointment appointment = booked(true);

        appointment.reassign(SALE_B, LEADER, LATER);

        assertEquals(SALE_B, appointment.getSurveyorUserId());
        assertEquals(List.of(new Holder(SALE_A, BOOKED_AT, LATER), new Holder(SALE_B, LATER, null)),
                appointment.holders());
        assertEquals(ConfirmationCause.REASSIGNED, appointment.getConfirmation().cause());
        AppointmentEvent event = lastEvent(appointment);
        assertEquals(AppointmentEventType.SURVEYOR_CHANGED, event.type());
        assertEquals(SALE_A, event.fromSurveyorUserId());
        assertEquals(SALE_B, event.toSurveyorUserId());
        assertEquals(ConfirmationCause.REASSIGNED, event.confirmationCause());
    }

    /** Review Focus 1: rows written within a minute are in force for that whole minute. */
    @Test
    void revisionAndHolder_inForceAtTheSameMinute_areFound() {
        SurveyAppointment appointment = booked();
        appointment.reassign(SALE_B, LEADER, Instant.parse("2026-10-23T03:20:40Z"));

        assertEquals(SALE_A, appointment.holderAt(Instant.parse("2026-10-23T03:05:00Z")));
        assertEquals(SALE_A, appointment.holderAt(Instant.parse("2026-10-23T03:19:00Z")));
        assertEquals(SALE_B, appointment.holderAt(Instant.parse("2026-10-23T03:20:00Z")));
        assertEquals(1, appointment.revisionInForceAt(Instant.parse("2026-10-23T03:05:00Z")).revisionNo());
        assertNull(appointment.holderAt(Instant.parse("2026-10-23T03:04:00Z")));
    }

    // ==================== visit ====================

    @Test
    void recordVisit_ownArrivalBeforeTheStart_isVisitedWithArrivalAndRecordingTime() {
        SurveyAppointment appointment = booked();
        Instant arrived = START.minus(Duration.ofMinutes(30));
        Instant recorded = START.plus(Duration.ofHours(11));

        appointment.recordVisit(arrived, "Met the owner at the gate", SALE_A, SALE_A, VisitRecordKind.OWN, null, null,
                recorded);

        assertEquals(AppointmentStatus.VISITED, appointment.getStatus());
        VisitRecord visit = appointment.getVisit();
        assertEquals(VisitRecordKind.OWN, visit.kind());
        assertEquals(SALE_A, visit.performedBy());
        assertEquals(arrived, visit.arrivedAt());
        assertEquals(1, visit.revisionNo());
        assertEquals("Met the owner at the gate", visit.note());
        assertEquals(SALE_A, visit.recordedBy());
        assertEquals(recorded, visit.recordedAt());
        assertNull(visit.proxyReason());
        AppointmentEvent event = lastEvent(appointment);
        assertEquals(AppointmentEventType.VISITED, event.type());
        assertEquals(recorded, event.occurredAt());
        assertEquals(SALE_A, event.actorUserId());
    }

    @Test
    void recordVisit_arrivalRules_ofTheTimeRules() {
        Instant recorded = START.plus(Duration.ofHours(11));
        assertArrivalRefused(START.minus(Duration.ofMinutes(61)), recorded);
        assertArrivalRefused(recorded.plus(Duration.ofMinutes(1)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES),
                recorded);
        assertArrivalRefused(Instant.parse("2026-10-23T03:04:00Z"), recorded);
        assertArrivalRefused(START.plusSeconds(30), recorded);
    }

    @Test
    void recordVisit_inTheBookingMinute_isAccepted() {
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_ID, 1,
                AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, leadPlace(),
                details(Instant.parse("2026-10-23T03:30:00Z"), 60, atLead()), null, SALE_A, false, null, SALE_A,
                BOOKED_AT);

        appointment.recordVisit(Instant.parse("2026-10-23T03:05:00Z"), "Already there", SALE_A, SALE_A,
                VisitRecordKind.OWN, null, null, BOOKED_AT.plusSeconds(10));

        assertEquals(AppointmentStatus.VISITED, appointment.getStatus());
    }

    /** W15-19: the 60-minute bound and the revision number come from the revision in force at the arrival. */
    @Test
    void recordVisit_afterALaterEdit_usesTheRevisionInForceAtTheArrival() {
        // Start 10:00; at 09:30:20 the start moves to 12:00 (revision 2). All times Vietnam time on the 24th.
        Instant editAt = Instant.parse("2026-10-24T02:30:20Z");
        Instant newStart = Instant.parse("2026-10-24T05:00:00Z");
        Instant recordedAt = Instant.parse("2026-10-24T06:00:00Z");

        SurveyAppointment early = editedAt(editAt, newStart);
        early.recordVisit(Instant.parse("2026-10-24T02:15:00Z"), "Before the edit", SALE_A, SALE_A,
                VisitRecordKind.OWN, null, null, recordedAt);
        assertEquals(1, early.getVisit().revisionNo());

        SurveyAppointment inTheEditMinute = editedAt(editAt, newStart);
        assertEquals("arrivedAt", assertThrows(InvalidSurveyValueException.class, () -> inTheEditMinute.recordVisit(
                Instant.parse("2026-10-24T02:30:00Z"), "Too early for 12:00", SALE_A, SALE_A, VisitRecordKind.OWN,
                null, null, recordedAt)).getField());

        SurveyAppointment late = editedAt(editAt, newStart);
        late.recordVisit(Instant.parse("2026-10-24T04:10:00Z"), "Before the new start", SALE_A, SALE_A,
                VisitRecordKind.OWN, null, null, recordedAt);
        assertEquals(2, late.getVisit().revisionNo());
    }

    private static SurveyAppointment editedAt(Instant editAt, Instant newStart) {
        SurveyAppointment appointment = booked();
        appointment.change(details(newStart, 60, atLead()), "Owner asked for noon", SALE_A, editAt);
        return appointment;
    }

    @Test
    void recordVisit_performerWhoDidNotHoldIt_isAMismatch() {
        SurveyAppointment appointment = booked();
        appointment.reassign(SALE_B, LEADER, START.plus(Duration.ofHours(1)));

        assertThrows(PerformerMismatchException.class, () -> appointment.recordVisit(START, "Visited", SALE_B, SALE_B,
                VisitRecordKind.OWN, null, null, START.plus(Duration.ofHours(2))));
    }

    @Test
    void recordVisit_ownWithAnotherRecorder_orProxyWithoutBasis_isABug() {
        assertThrows(IllegalArgumentException.class, () -> booked().recordVisit(START, "x", SALE_A, LEADER,
                VisitRecordKind.OWN, null, null, START.plusSeconds(600)));
        assertThrows(IllegalArgumentException.class, () -> booked().recordVisit(START, "x", SALE_A, LEADER,
                VisitRecordKind.PROXY, "reason", null, START.plusSeconds(600)));
    }

    @Test
    void recordVisit_proxyBasisBeforeTheArrivalDate_isRefused() {
        Basis basis = Basis.of("proxy.basis", LocalDate.parse("2026-10-23"), "Sale A told me");

        assertEquals("proxy.basis.confirmedOn", assertThrows(InvalidSurveyValueException.class,
                () -> booked().recordVisit(START, "Visited", SALE_A, LEADER, VisitRecordKind.PROXY,
                        "Sale A was offline", basis, START.plus(Duration.ofDays(1)))).getField());
    }

    @Test
    void recordVisit_proxy_keepsReasonAndBasis() {
        SurveyAppointment appointment = booked();
        Basis basis = Basis.of("proxy.basis", LocalDate.parse("2026-10-24"), "Sale A told me");

        appointment.recordVisit(START, "Visited", SALE_A, LEADER, VisitRecordKind.PROXY, "Sale A was offline", basis,
                START.plus(Duration.ofDays(1)));

        VisitRecord visit = appointment.getVisit();
        assertEquals(VisitRecordKind.PROXY, visit.kind());
        assertEquals(LEADER, visit.recordedBy());
        assertEquals("Sale A was offline", visit.proxyReason());
        assertEquals(basis, visit.basis());
        assertEquals(LEADER, lastEvent(appointment).actorUserId());
    }

    // ==================== final states ====================

    @Test
    void everyCommand_onVisitedOrCancelled_isAStateError() {
        SurveyAppointment visited = booked();
        visited.recordVisit(START, "Visited", SALE_A, SALE_A, VisitRecordKind.OWN, null, null,
                START.plusSeconds(600));
        SurveyAppointment cancelled = booked();
        cancelled.cancel(CancelReason.STAFF_CANCELLED, null, SALE_A, LATER);

        for (SurveyAppointment done : List.of(visited, cancelled)) {
            assertThrows(AppointmentStateException.class,
                    () -> done.change(details(START, 90, atLead()), "r", SALE_A, LATER));
            assertThrows(AppointmentStateException.class, () -> done.confirm(SALE_A, null, LATER));
            assertThrows(AppointmentStateException.class, () -> done.unconfirm(SALE_A, null, LATER));
            assertThrows(AppointmentStateException.class,
                    () -> done.cancel(CancelReason.OTHER, "again", SALE_A, LATER));
            assertThrows(AppointmentStateException.class,
                    () -> done.cancelBySystem(CancelReason.LEAD_CLOSED, "closed", SALE_A, LATER));
            assertThrows(AppointmentStateException.class, () -> done.reassign(SALE_B, LEADER, LATER));
            assertThrows(AppointmentStateException.class, () -> done.recordVisit(START, "again", SALE_A, SALE_A,
                    VisitRecordKind.OWN, null, null, START.plusSeconds(900)));
        }
    }

    @Test
    void markPersisted_clearsThePendingRows() {
        SurveyAppointment appointment = booked();

        appointment.markPersisted(0L);

        assertEquals(0L, appointment.getVersion());
        assertTrue(appointment.getPendingEvents().isEmpty());
        assertTrue(appointment.getPendingRevisions().isEmpty());
        assertEquals(1, appointment.getEvents().size());
    }

    // ==================== helpers ====================

    private static SurveyAppointment book(MeetingLocation place, String locationReason) {
        return SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_ID, 1, AppointmentKind.FIRST, null, null,
                SurveyIntent.LONG_TERM_RENT, 1, leadPlace(), details(START, 60, place), locationReason, SALE_A, false,
                null, SALE_A, BOOKED_AT);
    }

    private static void assertArrivalRefused(Instant arrivedAt, Instant now) {
        SurveyAppointment appointment = booked();
        InvalidSurveyValueException ex = assertThrows(InvalidSurveyValueException.class,
                () -> appointment.recordVisit(arrivedAt, "Visited", SALE_A, SALE_A, VisitRecordKind.OWN, null, null,
                        now), arrivedAt.toString());
        assertEquals("arrivedAt", ex.getField());
        assertEquals(AppointmentStatus.SCHEDULED, appointment.getStatus());
    }

    private static List<AppointmentEventType> types(SurveyAppointment appointment) {
        return appointment.getEvents().stream().map(AppointmentEvent::type).toList();
    }

    private static AppointmentEvent lastEvent(SurveyAppointment appointment) {
        List<AppointmentEvent> events = appointment.getEvents();
        return events.get(events.size() - 1);
    }
}
