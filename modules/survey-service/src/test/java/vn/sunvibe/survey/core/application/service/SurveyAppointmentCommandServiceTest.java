package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.sunvibe.survey.core.application.command.BookAppointmentCommand;
import vn.sunvibe.survey.core.application.command.CancelAppointmentCommand;
import vn.sunvibe.survey.core.application.command.ChangeAppointmentCommand;
import vn.sunvibe.survey.core.application.command.ConfirmationCommand;
import vn.sunvibe.survey.core.application.command.MeetingPlaceInput;
import vn.sunvibe.survey.core.application.command.ProxyRecord;
import vn.sunvibe.survey.core.application.command.RecordVisitCommand;
import vn.sunvibe.survey.core.application.command.ResumeSurveyCommand;
import vn.sunvibe.survey.core.application.exception.InvalidSurveyStateException;
import vn.sunvibe.survey.core.application.exception.NoCurrentVisitException;
import vn.sunvibe.survey.core.application.exception.SurveyConcurrentModificationException;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.exception.SurveyNotAllowedException;
import vn.sunvibe.survey.core.application.exception.SurveyorIneligibleException;
import vn.sunvibe.survey.core.application.query.AppointmentView;
import vn.sunvibe.survey.core.application.query.SurveyCommandResult;
import vn.sunvibe.survey.core.application.shared.SurveyBlockAssembler;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;
import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.exception.AppointmentStateException;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.exception.PerformerMismatchException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationState;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyAppointmentRepository;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyHistoryRepositories;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyLeadPort;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyorDirectory;
import vn.sunvibe.shared.generator.id.IdGenerator;

import com.github.f4b6a3.ulid.UlidCreator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Survey commands (survey-schedule spec 4.6, check order of 5.5: body 400 → lead visible 404 → versions 409 → state
 * 409 → stored values 400 → performer and eligibility 422 → no-op; lead first, survey rows after).
 */
class SurveyAppointmentCommandServiceTest {

    private static final String LEAD = "01JLEADAAAAAAAAAAAAAAAAAAA";
    private static final String OTHER_LEAD = "01JLEADBBBBBBBBBBBBBBBBBBB";
    private static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
    private static final String LEADER = "01JLEADERAAAAAAAAAAAAAAAAA";
    private static final SurveyViewer OWN_A = new SurveyViewer(SALE_A, ViewerAccess.OWN);
    private static final SurveyViewer OWN_B = new SurveyViewer(SALE_B, ViewerAccess.OWN);
    private static final SurveyViewer ALL_LEADER = new SurveyViewer(LEADER, ViewerAccess.ALL);

    private List<String> journal;
    private InMemorySurveyLeadPort leads;
    private InMemorySurveyAppointmentRepository appointments;
    private InMemorySurveyHistoryRepositories.Resumptions resumptions;
    private InMemorySurveyHistoryRepositories.AccessLogs accessLogs;
    private InMemorySurveyorDirectory directory;
    private SurveyAppointmentCommandService service;
    private Instant start;

    @BeforeEach
    void setUp() {
        journal = new ArrayList<>();
        leads = new InMemorySurveyLeadPort(journal);
        appointments = new InMemorySurveyAppointmentRepository(journal);
        resumptions = new InMemorySurveyHistoryRepositories.Resumptions();
        accessLogs = new InMemorySurveyHistoryRepositories.AccessLogs();
        directory = new InMemorySurveyorDirectory().withEligible(SALE_A, "Sale A").withEligible(SALE_B, "Sale B")
                .withEligible(LEADER, "Lead Er");
        IdGenerator<String> ulids = () -> UlidCreator.getMonotonicUlid().toString();
        IdGenerator<Long> snowflakes = new AtomicLong(5_000L)::incrementAndGet;
        SurveyBlockAssembler assembler = new SurveyBlockAssembler(appointments, resumptions, directory);
        service = new SurveyAppointmentCommandService(appointments, resumptions, accessLogs, leads, directory,
                assembler, ulids, snowflakes);
        start = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));
    }

    // ==================== book ====================

    @Test
    void book_firstSurveyOnOwnContactedLead_schedulesAndMovesTheLeadFirst() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 4L);

        SurveyCommandResult result = service.book(OWN_A, book(LEAD, 4L, start));

        AppointmentView view = result.survey().appointments().get(0);
        assertEquals(1, view.appointmentNo());
        assertEquals(AppointmentKind.FIRST, view.kind());
        assertEquals(AppointmentStatus.SCHEDULED, view.status());
        assertEquals(SALE_A, view.surveyorUserId());
        assertEquals(60, view.details().durationMinutes());
        assertEquals("SURVEY_SCHEDULED", result.lead().status());
        assertEquals(5L, result.lead().version());
        assertEquals("SURVEY_BOOKED", result.historyEntry().type());
        assertEquals(view.id(), result.historyEntry().referenceId());
        assertEquals(1, result.historyEntry().referenceNo());
        assertEquals(LeadMoveTarget.SURVEY_SCHEDULED, leads.moves().get(0).target());
        assertEquals(List.of("lead:SURVEY_BOOKED", "save:SCHEDULED"), journal);
        assertEquals(1, accessLogs.all().size());
        assertEquals(SALE_A, accessLogs.all().get(0).userId());
        assertEquals(LEAD, accessLogs.all().get(0).leadId());
        assertEquals(List.of(), result.overlaps());
        assertFalse(result.survey().booking().allowed());
    }

    @Test
    void book_overlappingAppointmentOfTheSurveyor_isListedButSaved() {
        leads.withLead(OTHER_LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        service.book(OWN_A, book(OTHER_LEAD, 0L, start.plus(Duration.ofMinutes(30))));
        leads.withLead(LEAD, "ASSIGNED", "SHORT_TERM_RENT", 1, SALE_A, 2L);

        SurveyCommandResult result = service.book(OWN_A, book(LEAD, 2L, start));

        assertEquals(1, result.overlaps().size());
        assertEquals(OTHER_LEAD, result.overlaps().get(0).leadId());
        assertEquals(leads.get(OTHER_LEAD).requestCode(), result.overlaps().get(0).requestCode());
        assertEquals(2, appointments.all().size());
    }

    @Test
    void book_invalidBodyOnUnknownLead_isA400First() {
        assertEquals("startAt", assertThrows(InvalidSurveyValueException.class,
                () -> service.book(OWN_A, book("01JUNKNOWNAAAAAAAAAAAAAAAA", 0L, start.plusSeconds(30)))).getField());
        assertEquals("meetingLocation.wardCode", assertThrows(InvalidSurveyValueException.class,
                () -> service.book(OWN_A, bookAt("01JUNKNOWNAAAAAAAAAAAAAAAA", 0L, start,
                        new MeetingPlaceInput("Cafe", "48", "99999", null, null), "reason"))).getField());
    }

    @Test
    void book_leadOfAnotherSale_isNotFound() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_B, 0L);

        assertThrows(SurveyLeadNotFoundException.class, () -> service.book(OWN_A, book(LEAD, 0L, start)));
        assertTrue(journal.isEmpty());
    }

    @Test
    void book_staleLeadVersion_isAConflictOnExpectedLeadVersion() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 3L);

        assertEquals("expectedLeadVersion", assertThrows(SurveyConcurrentModificationException.class,
                () -> service.book(OWN_A, book(LEAD, 2L, start))).getField());
    }

    @Test
    void book_existingScheduled_isRefusedBeforeStatusAndIntent() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        service.book(OWN_A, book(LEAD, 0L, start));
        LeadFacts lead = leads.get(LEAD);
        leads.put(new LeadFacts(LEAD, lead.requestCode(), "ON_HOLD", "SALE", 1, SALE_A, lead.version(),
                lead.lastActivityAt(), lead.contactName(), lead.place(), lead.createdAt(), null));

        assertThrows(AppointmentAlreadyScheduledException.class,
                () -> service.book(OWN_A, book(LEAD, lead.version(), start.plus(Duration.ofDays(1)))));
    }

    @Test
    void book_statusOrIntentThatDoNotAllowIt_isNotAllowedOnTheField() {
        leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 1, SALE_A, 0L);
        SurveyNotAllowedException status = assertThrows(SurveyNotAllowedException.class,
                () -> service.book(OWN_A, book(LEAD, 0L, start)));
        assertEquals("status", status.getField());
        assertEquals(List.of("ASSIGNED", "CONTACTED"), status.getAllowedValues());

        leads.withLead(OTHER_LEAD, "CONTACTED", "SALE", 1, SALE_A, 0L);
        SurveyNotAllowedException intent = assertThrows(SurveyNotAllowedException.class,
                () -> service.book(OWN_A, book(OTHER_LEAD, 0L, start)));
        assertEquals("intent", intent.getField());
        assertEquals(List.of("SHORT_TERM_RENT", "LONG_TERM_RENT"), intent.getAllowedValues());
    }

    @Test
    void book_resurveyWithoutReason_isRefusedOnResurveyReason_andWithItLinksTheVisit() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        SurveyAppointment visit = storedVisit(LEAD, 1, 1);

        assertEquals("resurveyReason", assertThrows(InvalidSurveyValueException.class,
                () -> service.book(OWN_A, book(LEAD, 0L, start))).getField());

        SurveyCommandResult result = service.book(OWN_A, new BookAppointmentCommand(LEAD, 0L, start, null, null,
                null, null, null, null, false, null, "Owner added a floor"));
        AppointmentView resurvey = result.survey().appointments().get(0);
        assertEquals(AppointmentKind.RESURVEY, resurvey.kind());
        assertEquals(visit.getId().getValue(), resurvey.previousVisitId());
        assertEquals(2, resurvey.appointmentNo());
        assertEquals("SURVEY_SCHEDULED", result.lead().status());
    }

    @Test
    void book_resurveyFromSurveyScheduled_keepsTheLeadStatus() {
        leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        storedVisit(LEAD, 1, 1);

        SurveyCommandResult result = service.book(OWN_A, new BookAppointmentCommand(LEAD, 0L, start, null, null,
                null, null, null, null, false, null, "Second look"));

        assertEquals(LeadMoveTarget.KEEP, leads.moves().get(0).target());
        assertNull(result.historyEntry().toStatus());
    }

    @Test
    void book_placeDifferentFromTheLeadWithoutReason_isRefusedOnLocationReason() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);

        assertEquals("locationReason", assertThrows(InvalidSurveyValueException.class,
                () -> service.book(OWN_A, bookAt(LEAD, 0L, start,
                        new MeetingPlaceInput("Cafe 3B", "48", "20195", null, null), null))).getField());

        SurveyCommandResult result = service.book(OWN_A, bookAt(LEAD, 0L, start,
                new MeetingPlaceInput("Cafe 3B", "48", "20195", null, null), "Owner meets us there"));
        MeetingLocation place = result.survey().appointments().get(0).details().meetingLocation();
        assertEquals("Thanh Khe", place.wardName());
        assertEquals("Da Nang", place.provinceName());
    }

    @Test
    void book_ineligibleAssignee_isRefusedWithoutField() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        directory.makeIneligible(SALE_A);

        assertNull(assertThrows(SurveyorIneligibleException.class,
                () -> service.book(ALL_LEADER, book(LEAD, 0L, start))).getField());
        assertTrue(appointments.all().isEmpty());
    }

    @Test
    void book_leadMoveFails_nothingIsSaved() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        leads.failNextApply = new LeadVersionConflictException();

        assertEquals("expectedLeadVersion", assertThrows(SurveyConcurrentModificationException.class,
                () -> service.book(OWN_A, book(LEAD, 0L, start))).getField());

        assertEquals(List.of("lead:SURVEY_BOOKED"), journal);
        assertTrue(appointments.all().isEmpty());
        assertTrue(accessLogs.all().isEmpty());
    }

    // ==================== change ====================

    @Test
    void change_time_addsARevisionUnconfirmsAndListsOverlaps() {
        String id = bookedOn(LEAD, true);
        leads.withLead(OTHER_LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        service.book(OWN_A, book(OTHER_LEAD, 0L, start.plus(Duration.ofHours(3))));

        SurveyCommandResult result = service.change(OWN_A, change(id, 1L, 0L, start.plus(Duration.ofHours(3))));

        AppointmentView view = view(result, id);
        assertEquals(2, view.revisions().size());
        assertEquals(ConfirmationState.UNCONFIRMED, view.confirmation().state());
        assertEquals("SURVEY_CHANGED", result.historyEntry().type());
        assertEquals(2L, result.lead().version());
        assertEquals(1, result.overlaps().size());
    }

    @Test
    void change_staleAppointmentVersion_isAConflictOnExpectedAppointmentVersion() {
        String id = bookedOn(LEAD, false);

        assertEquals("expectedAppointmentVersion", assertThrows(SurveyConcurrentModificationException.class,
                () -> service.change(OWN_A, change(id, 1L, 7L, start.plus(Duration.ofHours(1))))).getField());
    }

    @Test
    void change_ofAnotherSalesAppointment_isNotFound() {
        String id = bookedOn(LEAD, false);

        assertThrows(SurveyLeadNotFoundException.class,
                () -> service.change(OWN_B, change(id, 1L, 0L, start.plus(Duration.ofHours(1)))));
        assertThrows(SurveyLeadNotFoundException.class, () -> service.change(OWN_A,
                change(SurveyAppointmentId.generate().getValue(), 1L, 0L, start)));
        assertThrows(SurveyLeadNotFoundException.class, () -> service.change(OWN_A, change("x", 1L, 0L, start)));
    }

    @Test
    void change_byIneligibleSurveyor_is422AfterTheValueChecks() {
        String id = bookedOn(LEAD, false);
        directory.makeIneligible(SALE_A);

        assertThrows(InvalidSurveyValueException.class,
                () -> service.change(ALL_LEADER, change(id, 1L, 0L, start)));
        assertThrows(SurveyorIneligibleException.class,
                () -> service.change(ALL_LEADER, change(id, 1L, 0L, start.plus(Duration.ofHours(1)))));
    }

    // ==================== confirmation ====================

    @Test
    void confirm_sameValue_isANoOpWithoutHistoryEntryButLogged() {
        String id = bookedOn(LEAD, true);
        int logs = accessLogs.all().size();

        SurveyCommandResult result = service.setConfirmation(OWN_A,
                new ConfirmationCommand(id, 1L, 0L, true, null));

        assertNull(result.historyEntry());
        assertEquals(1L, result.lead().version());
        assertEquals(leads.get(LEAD).lastActivityAt(), result.lead().lastActivityAt());
        assertEquals(List.of("lead:SURVEY_BOOKED", "save:SCHEDULED"), journal);
        assertEquals(logs + 1, accessLogs.all().size());
    }

    @Test
    void unconfirm_recordsTheChange() {
        String id = bookedOn(LEAD, true);

        SurveyCommandResult result = service.setConfirmation(OWN_A,
                new ConfirmationCommand(id, 1L, 0L, false, "Owner unsure"));

        assertEquals("SURVEY_UNCONFIRMED", result.historyEntry().type());
        assertTrue(view(result, id).confirmation().needsReconfirmation());
    }

    @Test
    void confirm_ineligibleSurveyor_is422BeforeTheNoOp() {
        String id = bookedOn(LEAD, true);
        directory.makeIneligible(SALE_A);

        assertThrows(SurveyorIneligibleException.class, () -> service.setConfirmation(ALL_LEADER,
                new ConfirmationCommand(id, 1L, 0L, true, null)));
    }

    // ==================== cancel ====================

    @Test
    void cancel_firstSurvey_movesTheLeadBackToContacted() {
        String id = bookedOn(LEAD, false);

        SurveyCommandResult result = service.cancel(OWN_A,
                new CancelAppointmentCommand(id, 1L, 0L, CancelReason.OWNER_CANCELLED, null));

        assertEquals("CONTACTED", result.lead().status());
        assertEquals(LeadMoveTarget.CONTACTED, leads.moves().get(1).target());
        assertEquals(AppointmentStatus.CANCELLED, view(result, id).status());
    }

    /** survey-schedule E-5: a cancelled re-survey leaves the current visit, so the lead stays SURVEY_SCHEDULED. */
    @Test
    void cancel_resurveyWithACurrentVisit_keepsSurveyScheduled() {
        leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        storedVisit(LEAD, 1, 1);
        String id = service.book(OWN_A, new BookAppointmentCommand(LEAD, 0L, start, null, null, null, null, null,
                null, false, null, "Second look")).survey().appointments().get(0).id();

        SurveyCommandResult result = service.cancel(OWN_A,
                new CancelAppointmentCommand(id, 1L, 0L, CancelReason.OTHER, "Owner travels"));

        assertEquals("SURVEY_SCHEDULED", result.lead().status());
        assertEquals(LeadMoveTarget.KEEP, leads.moves().get(1).target());
    }

    @Test
    void cancel_visitedAppointment_isAStateError() {
        leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        SurveyAppointment visit = storedVisit(LEAD, 1, 1);

        assertThrows(AppointmentStateException.class, () -> service.cancel(OWN_A,
                new CancelAppointmentCommand(visit.getId().getValue(), 0L, 0L, CancelReason.STAFF_CANCELLED, null)));
    }

    // ==================== visit ====================

    @Test
    void recordVisit_saleForSomeoneElse_isForbiddenRightAfterTheBody() {
        assertThrows(AccessDeniedException.class, () -> service.recordVisit(OWN_A,
                new RecordVisitCommand("unknown", 0L, 0L, start, "Visited", SALE_B, null)));
        assertThrows(AccessDeniedException.class, () -> service.recordVisit(OWN_A,
                new RecordVisitCommand("unknown", 0L, 0L, start, "Visited", SALE_A, proxy(today()))));
    }

    @Test
    void recordVisit_own_isVisitedAndKeepsTheLead() {
        String id = bookedOnOverdue(LEAD);

        SurveyCommandResult result = service.recordVisit(OWN_A, new RecordVisitCommand(id, 1L, 0L,
                pastStart(), "Met the owner", SALE_A, null));

        AppointmentView view = view(result, id);
        assertEquals(AppointmentStatus.VISITED, view.status());
        assertEquals(VisitRecordKind.OWN, view.visit().kind());
        assertEquals("SURVEY_VISITED", result.historyEntry().type());
        assertEquals("SURVEY_SCHEDULED", result.lead().status());
        assertEquals(view.visit().recordedAt(), result.historyEntry().occurredAt());
    }

    @Test
    void recordVisit_performerWhoDidNotHoldIt_isAMismatch() {
        String id = bookedOnOverdue(LEAD);

        assertThrows(PerformerMismatchException.class, () -> service.recordVisit(ALL_LEADER,
                new RecordVisitCommand(id, 1L, 0L, pastStart(), "Visited", SALE_B, proxy(today()))));
    }

    @Test
    void recordVisit_managerForSomeoneElseWithoutProxy_isRefusedOnProxy() {
        String id = bookedOnOverdue(LEAD);

        assertEquals("proxy", assertThrows(InvalidSurveyValueException.class, () -> service.recordVisit(ALL_LEADER,
                new RecordVisitCommand(id, 1L, 0L, pastStart(), "Visited", SALE_A, null))).getField());
    }

    @Test
    void recordVisit_proxyByAManager_keepsReasonAndBasis() {
        String id = bookedOnOverdue(LEAD);

        SurveyCommandResult result = service.recordVisit(ALL_LEADER, new RecordVisitCommand(id, 1L, 0L,
                pastStart(), "Visited", SALE_A, proxy(SurveyTimeRules.vietnamDate(pastStart()))));

        assertEquals(VisitRecordKind.PROXY, view(result, id).visit().kind());
        assertEquals(LEADER, view(result, id).visit().recordedBy());
    }

    @Test
    void recordVisit_ownByACallerNoLongerEligible_is422OnPerformer() {
        String id = bookedOnOverdue(LEAD);
        directory.makeIneligible(SALE_A);

        assertEquals("performedByUserId", assertThrows(SurveyorIneligibleException.class,
                () -> service.recordVisit(OWN_A, new RecordVisitCommand(id, 1L, 0L, pastStart(), "Visited", SALE_A,
                        null))).getField());
    }

    // ==================== resume ====================

    @Test
    void resume_heldLeadWithACurrentVisit_movesToSurveyScheduledAndRecordsTheResumption() {
        SurveyAppointment visit = heldLeadWithVisit();

        SurveyCommandResult result = service.resume(ALL_LEADER, new ResumeSurveyCommand(LEAD, 0L,
                "Owner is back", Basis.of("basis", LocalDate.now(SurveyTimeRules.ZONE), "Owner confirmed scope")));

        assertEquals("SURVEY_SCHEDULED", result.lead().status());
        assertEquals("SURVEY_RESUMED", result.historyEntry().type());
        assertEquals(visit.getId().getValue(), result.historyEntry().referenceId());
        assertEquals(1, resumptions.all().size());
        assertEquals(visit.getId(), resumptions.all().get(0).visitAppointmentId());
        assertEquals(1, result.survey().resumptions().size());
        assertTrue(result.survey().currentVisitId().equals(visit.getId().getValue()));
    }

    @Test
    void resume_withoutCurrentVisit_isNoCurrentVisit_andNotHeld_isAStateError() {
        leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 1, SALE_A, 0L);
        assertThrows(NoCurrentVisitException.class, () -> service.resume(ALL_LEADER, resume(LEAD)));

        leads.withLead(OTHER_LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        InvalidSurveyStateException ex = assertThrows(InvalidSurveyStateException.class,
                () -> service.resume(ALL_LEADER, resume(OTHER_LEAD)));
        assertEquals(List.of("ON_HOLD"), ex.getAllowedValues());
    }

    @Test
    void resume_basisBeforeTheHoldDate_isRefused() {
        heldLeadWithVisit();
        LeadFacts lead = leads.get(LEAD);
        Instant heldAt = Instant.now().minus(Duration.ofDays(1));
        leads.put(new LeadFacts(LEAD, lead.requestCode(), lead.status(), lead.intent(), 1, SALE_A, lead.version(),
                lead.lastActivityAt(), lead.contactName(), lead.place(), lead.createdAt(), heldAt));

        assertEquals("basis.confirmedOn", assertThrows(InvalidSurveyValueException.class,
                () -> service.resume(ALL_LEADER, new ResumeSurveyCommand(LEAD, 0L, "Back",
                        Basis.of("basis", SurveyTimeRules.vietnamDate(heldAt).minusDays(1), "Old talk")))).getField());
    }

    // ==================== helpers ====================

    private BookAppointmentCommand book(String leadId, long version, Instant startAt) {
        return new BookAppointmentCommand(leadId, version, startAt, null, null, null, null, null, null, false, null,
                null);
    }

    private BookAppointmentCommand bookAt(String leadId, long version, Instant startAt, MeetingPlaceInput place,
                                          String reason) {
        return new BookAppointmentCommand(leadId, version, startAt, null, place, reason, null, null, null, false,
                null, null);
    }

    private static LocalDate today() {
        return LocalDate.now(SurveyTimeRules.ZONE);
    }

    private ChangeAppointmentCommand change(String id, long leadVersion, long appointmentVersion, Instant startAt) {
        return new ChangeAppointmentCommand(id, leadVersion, appointmentVersion, "Owner asked", startAt, 60,
                new MeetingPlaceInput("12 Bach Dang", "48", "20194", new BigDecimal("16.054407"),
                        new BigDecimal("108.202167")), null, null, null);
    }

    private static ResumeSurveyCommand resume(String leadId) {
        return new ResumeSurveyCommand(leadId, 0L, "Owner is back",
                Basis.of("basis", LocalDate.now(SurveyTimeRules.ZONE), "Owner confirmed scope"));
    }

    private static ProxyRecord proxy(LocalDate confirmedOn) {
        return new ProxyRecord("Sale A had no signal", Basis.of("proxy.basis", confirmedOn, "Sale A called me"));
    }

    /** A scheduled appointment booked through the service on a CONTACTED lead of SALE_A; the lead is then version 1. */
    private String bookedOn(String leadId, boolean ownerConfirmed) {
        leads.withLead(leadId, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        return service.book(OWN_A, new BookAppointmentCommand(leadId, 0L, start, null, null, null, null, null, null,
                ownerConfirmed, null, null)).survey().appointments().get(0).id();
    }

    private Instant pastStart() {
        return Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofHours(2));
    }

    /** A scheduled appointment of SALE_A that started two hours ago (booked three hours ago); lead version 1. */
    private String bookedOnOverdue(String leadId) {
        leads.withLead(leadId, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 1L);
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId, 1,
                AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, snapshot(),
                AppointmentDetails.of(pastStart(), 60, MeetingLocation.fromLead(snapshot()), null, null, null), null,
                SALE_A, false, null, SALE_A, pastStart().minus(Duration.ofHours(1)));
        appointments.save(appointment);
        journal.clear();
        return appointment.getId().getValue();
    }

    private SurveyAppointment storedVisit(String leadId, int no, int cycle) {
        Instant visitStart = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofDays(2));
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId, no,
                AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, cycle, snapshot(),
                AppointmentDetails.of(visitStart, 60, MeetingLocation.fromLead(snapshot()), null, null, null), null,
                SALE_A, false, null, SALE_A, visitStart.minus(Duration.ofHours(1)));
        appointment.recordVisit(visitStart, "Visited", SALE_A, SALE_A, VisitRecordKind.OWN, null, null,
                visitStart.plus(Duration.ofHours(1)));
        appointments.save(appointment);
        journal.clear();
        return appointment;
    }

    private SurveyAppointment heldLeadWithVisit() {
        leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 1, SALE_A, 0L);
        return storedVisit(LEAD, 1, 1);
    }

    private static LeadLocationSnapshot snapshot() {
        return new LeadLocationSnapshot("12 Bach Dang", "48", "Da Nang", "20194", "Hai Chau",
                new Coordinates(new BigDecimal("16.054407"), new BigDecimal("108.202167")));
    }

    private static AppointmentView view(SurveyCommandResult result, String id) {
        return result.survey().appointments().stream().filter(view -> view.id().equals(id)).findFirst()
                .orElseThrow();
    }
}
