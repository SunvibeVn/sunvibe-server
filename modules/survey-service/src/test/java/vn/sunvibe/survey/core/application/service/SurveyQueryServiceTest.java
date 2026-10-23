package vn.sunvibe.survey.core.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.sunvibe.survey.core.application.exception.SurveyLeadNotFoundException;
import vn.sunvibe.survey.core.application.query.BookingBlock;
import vn.sunvibe.survey.core.application.query.BookingState;
import vn.sunvibe.survey.core.application.query.OverlapView;
import vn.sunvibe.survey.core.application.query.SurveyBlock;
import vn.sunvibe.survey.core.application.query.SurveyListItem;
import vn.sunvibe.survey.core.application.query.SurveyListQuery;
import vn.sunvibe.survey.core.application.query.SurveyListScope;
import vn.sunvibe.survey.core.application.shared.SurveyBlockAssembler;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;
import vn.sunvibe.survey.core.domain.exception.InvalidSurveyValueException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;
import vn.sunvibe.survey.core.domain.policy.ListGroup;
import vn.sunvibe.survey.core.domain.repository.PageResult;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyAppointmentRepository;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyHistoryRepositories;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyLeadPort;
import vn.sunvibe.survey.core.testsupport.InMemorySurveyorDirectory;
import vn.sunvibe.shared.generator.id.IdGenerator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Survey reads (survey-schedule US-4, spec 7.1, 7.2, A-11, A-13): the block with its booking state in check order and
 * one access-log row, the list per scope with the lead cards and without an access log, and the overlap pre-check.
 */
class SurveyQueryServiceTest {

    private static final String LEAD = "01JLEADAAAAAAAAAAAAAAAAAAA";
    private static final String LEAD_B = "01JLEADBBBBBBBBBBBBBBBBBBB";
    private static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
    private static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
    private static final String LEADER = "01JLEADERAAAAAAAAAAAAAAAAA";
    private static final SurveyViewer OWN_A = new SurveyViewer(SALE_A, ViewerAccess.OWN);
    private static final SurveyViewer ALL_LEADER = new SurveyViewer(LEADER, ViewerAccess.ALL);

    private InMemorySurveyLeadPort leads;
    private InMemorySurveyAppointmentRepository appointments;
    private InMemorySurveyHistoryRepositories.AccessLogs accessLogs;
    private InMemorySurveyorDirectory directory;
    private SurveyQueryService service;
    private Instant start;

    @BeforeEach
    void setUp() {
        List<String> journal = new ArrayList<>();
        leads = new InMemorySurveyLeadPort(journal);
        appointments = new InMemorySurveyAppointmentRepository(journal);
        InMemorySurveyHistoryRepositories.Resumptions resumptions = new InMemorySurveyHistoryRepositories.Resumptions();
        accessLogs = new InMemorySurveyHistoryRepositories.AccessLogs();
        directory = new InMemorySurveyorDirectory().withEligible(SALE_A, "Sale A").withEligible(SALE_B, "Sale B")
                .withName(LEADER, "Lead Er");
        IdGenerator<Long> snowflakes = new AtomicLong(1_000L)::incrementAndGet;
        service = new SurveyQueryService(appointments, accessLogs, leads, directory,
                new SurveyBlockAssembler(appointments, resumptions, directory), snowflakes);
        start = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofDays(1));
    }

    // ==================== block ====================

    @Test
    void block_visibleLead_writesOneAccessLogRowAndNamesStaffWithOneLookup() {
        leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 3L);
        scheduled(LEAD, 1, SALE_A, start);

        SurveyBlock block = service.block(OWN_A, LEAD);

        assertEquals(LEAD, block.leadId());
        assertEquals(1, block.intentCycle());
        assertEquals(1, block.appointments().size());
        assertEquals("Sale A", block.nameOf(SALE_A));
        assertEquals(1, directory.nameLookups);
        assertEquals(1, accessLogs.all().size());
        assertEquals(SALE_A, accessLogs.all().get(0).userId());
    }

    @Test
    void block_invisibleOrUnknownLead_isNotFoundWithoutLog() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_B, 0L);

        assertThrows(SurveyLeadNotFoundException.class, () -> service.block(OWN_A, LEAD));
        assertThrows(SurveyLeadNotFoundException.class, () -> service.block(ALL_LEADER, LEAD_B));
        assertTrue(accessLogs.all().isEmpty());
    }

    @Test
    void block_bookingState_followsTheBookingCheckOrder() {
        leads.withLead(LEAD, "SURVEY_SCHEDULED", "SALE", 1, SALE_A, 0L);
        scheduled(LEAD, 1, SALE_A, start);
        assertEquals(BookingBlock.ALREADY_SCHEDULED, service.block(ALL_LEADER, LEAD).booking().blockedBy());

        assertEquals(BookingBlock.NO_ASSIGNEE, booking("NEW", "LONG_TERM_RENT", null).blockedBy());
        assertEquals(BookingBlock.LEAD_STATUS, booking("ON_HOLD", "SALE", SALE_A).blockedBy());
        assertEquals(BookingBlock.INTENT, booking("CONTACTED", "SALE", SALE_A).blockedBy());
        directory.makeIneligible(SALE_B);
        assertEquals(BookingBlock.SURVEYOR_INELIGIBLE, booking("ASSIGNED", "LONG_TERM_RENT", SALE_B).blockedBy());

        BookingState allowed = booking("ASSIGNED", "SHORT_TERM_RENT", SALE_A);
        assertTrue(allowed.allowed());
        assertEquals(AppointmentKind.FIRST, allowed.kind());
        assertNull(allowed.blockedBy());
    }

    @Test
    void block_withACurrentVisit_offersARessurveyAndResumeOnHold() {
        leads.withLead(LEAD, "ON_HOLD", "LONG_TERM_RENT", 1, SALE_A, 0L);
        SurveyAppointment visit = visited(LEAD, 1);

        SurveyBlock held = service.block(ALL_LEADER, LEAD);
        assertTrue(held.resumeAllowed());
        assertEquals(visit.getId().getValue(), held.currentVisitId());
        assertEquals(BookingBlock.LEAD_STATUS, held.booking().blockedBy());

        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 1L);
        SurveyBlock contacted = service.block(ALL_LEADER, LEAD);
        assertFalse(contacted.resumeAllowed());
        assertEquals(AppointmentKind.RESURVEY, contacted.booking().kind());
        assertEquals(visit.getId().getValue(), contacted.booking().previousVisitId());
    }

    @Test
    void block_ineligibleSurveyor_flagsTheScheduledAppointmentOnly() {
        leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        visited(LEAD, 1);
        scheduled(LEAD, 2, SALE_A, start);
        directory.makeIneligible(SALE_A);

        SurveyBlock block = service.block(ALL_LEADER, LEAD);

        assertTrue(block.appointments().get(0).cannotBePerformed());
        assertFalse(block.appointments().get(1).cannotBePerformed());
        assertEquals(2, block.appointments().get(0).appointmentNo(), "newest first");
    }

    // ==================== list ====================

    @Test
    void list_saleMine_showsOwnScheduledWithCardsAndWritesNoLog() {
        leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        leads.withLead(LEAD_B, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_B, 0L);
        scheduled(LEAD, 1, SALE_A, start);
        scheduled(LEAD_B, 1, SALE_B, start);

        PageResult<SurveyListItem> page = service.list(OWN_A,
                new SurveyListQuery(SurveyListScope.MINE, Optional.empty(), 0, 20));

        assertEquals(1, page.items().size());
        SurveyListItem item = page.items().get(0);
        assertEquals(LEAD, item.leadId());
        assertEquals(leads.get(LEAD).requestCode(), item.requestCode());
        assertEquals(leads.get(LEAD).contactName(), item.ownerContactName());
        assertEquals("Hai Chau", item.wardName());
        assertEquals("Sale A", item.surveyorName());
        assertEquals(ListGroup.UPCOMING, item.group());
        assertFalse(item.cannotBePerformed());
        assertEquals(1L, page.totalElements());
        assertTrue(accessLogs.all().isEmpty());
    }

    @Test
    void list_saleWithScopeAll_isForbidden_andSurveyorWithMine_isRefused() {
        assertThrows(AccessDeniedException.class,
                () -> service.list(OWN_A, new SurveyListQuery(SurveyListScope.ALL, Optional.empty(), 0, 20)));
        assertEquals("surveyorUserId", assertThrows(InvalidSurveyValueException.class,
                () -> new SurveyListQuery(SurveyListScope.MINE, Optional.of(SALE_A), 0, 20)).getField());
        assertEquals("size", assertThrows(InvalidSurveyValueException.class,
                () -> new SurveyListQuery(SurveyListScope.ALL, Optional.empty(), 0, 101)).getField());
    }

    @Test
    void list_leaderAllWithSurveyorFilter_andFlagsIneligibleSurveyors() {
        leads.withLead(LEAD, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        leads.withLead(LEAD_B, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_B, 0L);
        scheduled(LEAD, 1, SALE_A, start);
        scheduled(LEAD_B, 1, SALE_B, start.plus(Duration.ofHours(1)));
        directory.makeIneligible(SALE_B);

        PageResult<SurveyListItem> all = service.list(ALL_LEADER,
                new SurveyListQuery(SurveyListScope.ALL, Optional.empty(), 0, 20));
        PageResult<SurveyListItem> ofB = service.list(ALL_LEADER,
                new SurveyListQuery(SurveyListScope.ALL, Optional.of(SALE_B), 0, 20));

        assertEquals(List.of(LEAD, LEAD_B), all.items().stream().map(SurveyListItem::leadId).toList());
        assertEquals(List.of(LEAD_B), ofB.items().stream().map(SurveyListItem::leadId).toList());
        assertTrue(ofB.items().get(0).cannotBePerformed());
    }

    // ==================== overlaps ====================

    @Test
    void overlaps_ofTheLeadsSurveyor_areListedWithoutLog() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        leads.withLead(LEAD_B, "SURVEY_SCHEDULED", "LONG_TERM_RENT", 1, SALE_A, 0L);
        SurveyAppointment other = scheduled(LEAD_B, 1, SALE_A, start.plus(Duration.ofMinutes(30)));

        List<OverlapView> overlaps = service.overlaps(OWN_A, LEAD, start, 60, Optional.empty());
        List<OverlapView> excluded = service.overlaps(OWN_A, LEAD, start, 60,
                Optional.of(other.getId().getValue()));

        assertEquals(1, overlaps.size());
        assertEquals(other.getId().getValue(), overlaps.get(0).appointmentId());
        assertEquals(leads.get(LEAD_B).requestCode(), overlaps.get(0).requestCode());
        assertEquals(List.of(), excluded);
        assertTrue(accessLogs.all().isEmpty());
    }

    @Test
    void overlaps_badValues_areRefusedOnTheirField() {
        leads.withLead(LEAD, "CONTACTED", "LONG_TERM_RENT", 1, SALE_A, 0L);

        assertEquals("startAt", assertThrows(InvalidSurveyValueException.class,
                () -> service.overlaps(OWN_A, LEAD, start.plusSeconds(1), 60, Optional.empty())).getField());
        assertEquals("durationMinutes", assertThrows(InvalidSurveyValueException.class,
                () -> service.overlaps(OWN_A, LEAD, start, 50, Optional.empty())).getField());
        assertEquals("excludeAppointmentId", assertThrows(InvalidSurveyValueException.class,
                () -> service.overlaps(OWN_A, LEAD, start, 60, Optional.of("x"))).getField());
        assertThrows(SurveyLeadNotFoundException.class,
                () -> service.overlaps(new SurveyViewer(SALE_B, ViewerAccess.OWN), LEAD, start, 60,
                        Optional.empty()));
    }

    // ==================== helpers ====================

    private BookingState booking(String status, String intent, String assignee) {
        String leadId = "01JLEADC" + Long.toString(System.nanoTime(), 36).toUpperCase().substring(0, 8)
                + "AAAAAAAAAA";
        leads.withLead(leadId, status, intent, 1, assignee, 0L);
        return service.block(ALL_LEADER, leadId).booking();
    }

    private SurveyAppointment scheduled(String leadId, int no, String surveyor, Instant startAt) {
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId, no,
                AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, snapshot(),
                AppointmentDetails.of(startAt, 60, MeetingLocation.fromLead(snapshot()), null, null, null), null,
                surveyor, false, null, surveyor, Instant.now());
        appointments.save(appointment);
        return appointment;
    }

    private SurveyAppointment visited(String leadId, int no) {
        Instant visitStart = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(Duration.ofDays(2));
        SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId, no,
                AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, snapshot(),
                AppointmentDetails.of(visitStart, 60, MeetingLocation.fromLead(snapshot()), null, null, null), null,
                SALE_A, false, null, SALE_A, visitStart.minus(Duration.ofHours(1)));
        appointment.recordVisit(visitStart, "Visited", SALE_A, SALE_A, VisitRecordKind.OWN, null, null,
                visitStart.plus(Duration.ofHours(1)));
        appointments.save(appointment);
        return appointment;
    }

    private static LeadLocationSnapshot snapshot() {
        return new LeadLocationSnapshot("12 Bach Dang", "48", "Da Nang", "20194", "Hai Chau",
                new Coordinates(new BigDecimal("16.054407"), new BigDecimal("108.202167")));
    }
}
