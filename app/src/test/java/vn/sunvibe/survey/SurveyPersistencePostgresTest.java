package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.survey.core.domain.exception.AppointmentAlreadyScheduledException;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentDetails;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEventType;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.Basis;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;
import vn.sunvibe.survey.core.domain.model.appointment.Coordinates;
import vn.sunvibe.survey.core.domain.model.appointment.Holder;
import vn.sunvibe.survey.core.domain.model.appointment.LeadLocationSnapshot;
import vn.sunvibe.survey.core.domain.model.appointment.MeetingLocation;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointmentId;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;
import vn.sunvibe.survey.core.domain.model.log.SurveyAccessLog;
import vn.sunvibe.survey.core.domain.model.resumption.SurveyResumption;
import vn.sunvibe.survey.core.domain.repository.PageResult;
import vn.sunvibe.survey.core.domain.repository.ScheduledSearch;
import vn.sunvibe.survey.core.domain.repository.SurveyAccessLogDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.domain.repository.SurveyResumptionDomainRepository;

/**
 * The survey tables built by Flyway and the survey adapters on a real PostgreSQL (survey-schedule spec 5.2): the
 * appointment with its revisions, events and holders round-trips; one scheduled appointment per lead (I-1); the
 * CHECKs refuse broken rows; the list search groups and orders with the given "now"; overlaps are found per surveyor.
 * Needs Docker, like {@code FlywayPostgresMigrationTest}.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class SurveyPersistencePostgresTest {

	private static final String LEAD_1 = "01JLEADSURVEY1AAAAAAAAAAAA";
	private static final String LEAD_2 = "01JLEADSURVEY2AAAAAAAAAAAA";
	private static final String LEAD_3 = "01JLEADSURVEY3AAAAAAAAAAAA";
	private static final String SALE_A = "01JSALEAAAAAAAAAAAAAAAAAAA";
	private static final String SALE_B = "01JSALEBBBBBBBBBBBBBBBBBBB";
	private static final String LEADER = "01JLEADERAAAAAAAAAAAAAAAAA";
	/** 2026-10-23 10:05:30.123456 Vietnam time (microseconds: PostgreSQL keeps no more). */
	private static final Instant BOOKED_AT = Instant.parse("2026-10-23T03:05:30.123456Z");
	private static final Instant START = Instant.parse("2026-10-24T03:00:00Z");

	@Autowired
	private SurveyAppointmentDomainRepository appointments;

	@Autowired
	private SurveyResumptionDomainRepository resumptions;

	@Autowired
	private SurveyAccessLogDomainRepository accessLogs;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void cleanSurveyTables() {
		jdbc.update("DELETE FROM survey_access_logs");
		jdbc.update("DELETE FROM survey_resumptions");
		jdbc.update("DELETE FROM survey_appointment_events");
		jdbc.update("DELETE FROM survey_appointment_revisions");
		jdbc.update("DELETE FROM survey_appointments");
	}

	// ==================== round trip ====================

	@Test
	void roundTrip_bookedChangedReassignedAndVisited_reloadsEveryFieldRevisionEventAndHolder() {
		SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_1, 1,
				AppointmentKind.FIRST, null, null, SurveyIntent.SHORT_TERM_RENT, 2, leadPlace(),
				AppointmentDetails.of(START, 90, cafe(), "Ring twice", "Anh Tuan", "Guard"), "Meet at the cafe",
				SALE_A, true, "Owner agreed", SALE_A, BOOKED_AT);
		appointments.save(appointment);
		assertEquals(0L, appointment.getVersion());

		Instant changedAt = BOOKED_AT.plus(Duration.ofHours(1));
		appointment.change(AppointmentDetails.of(START.plus(Duration.ofHours(1)), 60, cafe(), null, null, null),
				"Owner asked for later", LEADER, changedAt);
		appointments.save(appointment);
		Instant reassignedAt = BOOKED_AT.plus(Duration.ofHours(2));
		appointment.reassign(SALE_B, LEADER, reassignedAt);
		appointments.save(appointment);
		Instant recordedAt = START.plus(Duration.ofHours(5));
		Basis basis = Basis.of("proxy.basis", LocalDate.parse("2026-10-24"), "Sale B called me");
		appointment.recordVisit(START.plus(Duration.ofMinutes(50)), "Visited with the owner", SALE_B, LEADER,
				VisitRecordKind.PROXY, "Sale B had no signal", basis, recordedAt);
		appointments.save(appointment);
		assertEquals(3L, appointment.getVersion());

		SurveyAppointment stored = appointments.findById(appointment.getId()).orElseThrow();

		assertEquals(appointment.getId(), stored.getId());
		assertEquals(LEAD_1, stored.getLeadId());
		assertEquals(1, stored.getAppointmentNo());
		assertEquals(AppointmentKind.FIRST, stored.getKind());
		assertEquals(SurveyIntent.SHORT_TERM_RENT, stored.getIntent());
		assertEquals(2, stored.getIntentCycle());
		assertEquals(AppointmentStatus.VISITED, stored.getStatus());
		assertEquals(appointment.getDetails(), stored.getDetails());
		assertEquals(2, stored.getCurrentRevisionNo());
		assertEquals(leadPlace(), stored.getLeadLocation());
		assertEquals("Meet at the cafe", stored.getLocationReason());
		assertEquals(SALE_B, stored.getSurveyorUserId());
		assertEquals(appointment.getConfirmation(), stored.getConfirmation());
		assertEquals(appointment.getVisit(), stored.getVisit());
		assertNull(stored.getCancellation());
		assertEquals(SALE_A, stored.getCreatedBy());
		assertEquals(BOOKED_AT, stored.getCreatedAt());
		assertEquals(recordedAt, stored.getUpdatedAt());
		assertEquals(3L, stored.getVersion());
		assertEquals(appointment.getRevisions(), stored.getRevisions());
		assertEquals(appointment.getEvents(), stored.getEvents());
		assertEquals(List.of(AppointmentEventType.BOOKED, AppointmentEventType.CONFIRMED, AppointmentEventType.CHANGED,
				AppointmentEventType.SURVEYOR_CHANGED, AppointmentEventType.VISITED),
				stored.getEvents().stream().map(event -> event.type()).toList());
		assertEquals(List.of(new Holder(SALE_A, BOOKED_AT, reassignedAt), new Holder(SALE_B, reassignedAt, null)),
				stored.holders());
		assertTrue(stored.getPendingEvents().isEmpty());
	}

	@Test
	void roundTrip_resurveyAndCancellation_reloads() {
		SurveyAppointment visit = visited(LEAD_1, 1, START);
		SurveyAppointment resurvey = SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_1, 2,
				AppointmentKind.RESURVEY, visit.getId(), "Owner added a floor", SurveyIntent.LONG_TERM_RENT, 1,
				leadPlace(), details(START.plus(Duration.ofDays(2)), 60), null, SALE_A, false, null, SALE_A,
				BOOKED_AT);
		appointments.save(resurvey);
		resurvey.cancelBySystem(CancelReason.LEAD_ON_HOLD, "x".repeat(2000), LEADER, BOOKED_AT.plusSeconds(60));
		appointments.save(resurvey);

		SurveyAppointment stored = appointments.findById(resurvey.getId()).orElseThrow();

		assertEquals(visit.getId(), stored.getPreviousVisitId());
		assertEquals("Owner added a floor", stored.getResurveyReason());
		assertEquals(resurvey.getCancellation(), stored.getCancellation());
		assertEquals(List.of(2, 1), appointments.findByLeadId(LEAD_1).stream()
				.map(SurveyAppointment::getAppointmentNo).toList());
		assertEquals(3, appointments.nextAppointmentNo(LEAD_1));
		assertEquals(1, appointments.nextAppointmentNo(LEAD_2));
	}

	@Test
	void save_staleVersion_isAnOptimisticLockFailure() {
		SurveyAppointment appointment = booked(LEAD_1, 1, START, SALE_A);
		SurveyAppointment copy = appointments.findById(appointment.getId()).orElseThrow();
		appointment.confirm(SALE_A, null, BOOKED_AT.plusSeconds(60));
		appointments.save(appointment);

		copy.cancel(CancelReason.STAFF_CANCELLED, null, SALE_A, BOOKED_AT.plusSeconds(120));

		assertThrows(org.springframework.dao.OptimisticLockingFailureException.class, () -> appointments.save(copy));
	}

	// ==================== one scheduled appointment per lead (I-1) ====================

	@Test
	void secondScheduled_forOneLead_isRefused() {
		booked(LEAD_1, 1, START, SALE_A);
		SurveyAppointment second = SurveyAppointment.book(SurveyAppointmentId.generate(), LEAD_1, 2,
				AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, leadPlace(), details(START, 60),
				null, SALE_A, false, null, SALE_A, BOOKED_AT);

		assertThrows(AppointmentAlreadyScheduledException.class, () -> appointments.save(second));

		visited(LEAD_2, 1, START);
		booked(LEAD_2, 2, START.plus(Duration.ofDays(1)), SALE_A);
		assertEquals(Optional.of(2), appointments.findScheduledByLeadId(LEAD_2).map(SurveyAppointment::getAppointmentNo));
		assertEquals(Optional.empty(), appointments.findScheduledByLeadId(LEAD_3));
	}

	// ==================== CHECK constraints ====================

	@Test
	void checks_refuseBrokenRows() {
		SurveyAppointment appointment = booked(LEAD_1, 1, START, SALE_A);
		String id = "'" + appointment.getId().getValue() + "'";

		assertRejected("UPDATE survey_appointments SET duration_minutes = 50 WHERE id = " + id,
				"chk_survey_appointments_duration");
		assertRejected("UPDATE survey_appointments SET kind = 'RESURVEY' WHERE id = " + id,
				"chk_survey_appointments_resurvey");
		assertRejected("UPDATE survey_appointments SET status = 'CANCELLED' WHERE id = " + id,
				"chk_survey_appointments_cancellation");
		assertRejected("UPDATE survey_appointments SET status = 'VISITED', visit_record_kind = 'OWN', "
				+ "performed_by = '" + SALE_A + "', arrived_at = start_at, visit_revision_no = 1, visit_note = 'n', "
				+ "visit_recorded_by = '" + SALE_A + "', visit_recorded_at = start_at, proxy_reason = 'r', "
				+ "basis_confirmed_on = DATE '2026-10-24', basis_text = 't' WHERE id = " + id,
				"chk_survey_appointments_proxy");
		assertRejected("UPDATE survey_appointments SET status = 'VISITED', visit_record_kind = 'OWN', "
				+ "performed_by = '" + SALE_A + "', arrived_at = start_at, visit_revision_no = 1, visit_note = 'n', "
				+ "visit_recorded_by = '" + LEADER + "', visit_recorded_at = start_at WHERE id = " + id,
				"chk_survey_appointments_own_record");
		assertRejected("UPDATE survey_appointments SET confirmation_cause = NULL WHERE id = " + id,
				"chk_survey_appointments_confirmation");
		assertRejected("UPDATE survey_appointments SET meeting_latitude = NULL WHERE id = " + id,
				"chk_survey_appointments_meeting_pin");
	}

	// ==================== list search ====================

	/** Review Focus 4: at 00:30 an appointment that started yesterday 23:00 for 480 minutes is still running. */
	@Test
	void search_runningSinceYesterday_isToday() {
		Instant now = Instant.parse("2026-10-23T17:30:00Z");
		SurveyAppointment running = bookedAt(LEAD_1, Instant.parse("2026-10-23T16:00:00Z"), 480, SALE_A);

		PageResult<SurveyAppointment> page = appointments.searchScheduled(new ScheduledSearch(Optional.empty()), now,
				Instant.parse("2026-10-24T17:00:00Z"), 0, 20);

		assertEquals(List.of(running.getId()), page.items().stream().map(SurveyAppointment::getId).toList());
	}

	@Test
	void search_ordersByGroupThenStart_acrossPages() {
		Instant now = Instant.parse("2026-10-24T03:00:00Z");
		Instant tomorrow = Instant.parse("2026-10-24T17:00:00Z");
		SurveyAppointment upcomingLate = bookedAt(LEAD_1, Instant.parse("2026-10-26T03:00:00Z"), 60, SALE_A);
		SurveyAppointment today = bookedAt(LEAD_2, Instant.parse("2026-10-24T05:00:00Z"), 60, SALE_B);
		SurveyAppointment overdue = bookedAt(LEAD_3, Instant.parse("2026-10-24T01:00:00Z"), 60, SALE_A);
		SurveyAppointment upcoming = bookedAt("01JLEADSURVEY4AAAAAAAAAAAA", Instant.parse("2026-10-25T03:00:00Z"),
				60, SALE_A);
		visited("01JLEADSURVEY5AAAAAAAAAAAA", 1, Instant.parse("2026-10-24T02:00:00Z"));

		PageResult<SurveyAppointment> first = appointments.searchScheduled(new ScheduledSearch(Optional.empty()), now,
				tomorrow, 0, 2);
		PageResult<SurveyAppointment> second = appointments.searchScheduled(new ScheduledSearch(Optional.empty()), now,
				tomorrow, 1, 2);
		PageResult<SurveyAppointment> ofSaleA = appointments.searchScheduled(new ScheduledSearch(Optional.of(SALE_A)),
				now, tomorrow, 0, 20);

		assertEquals(List.of(overdue.getId(), today.getId()), ids(first));
		assertEquals(List.of(upcoming.getId(), upcomingLate.getId()), ids(second));
		assertEquals(4L, first.totalElements());
		assertEquals(List.of(overdue.getId(), upcoming.getId(), upcomingLate.getId()), ids(ofSaleA));
		assertEquals(3L, ofSaleA.totalElements());
	}

	// ==================== overlaps ====================

	@Test
	void findOverlapping_onlyScheduledOfTheSameSurveyor_touchingDoesNotOverlap() {
		SurveyAppointment nine = bookedAt(LEAD_1, Instant.parse("2026-10-24T02:00:00Z"), 60, SALE_A);
		bookedAt(LEAD_2, Instant.parse("2026-10-24T02:30:00Z"), 60, SALE_B);
		visited(LEAD_3, 1, Instant.parse("2026-10-24T02:15:00Z"));

		List<SurveyAppointment> overlapping = appointments.findOverlapping(SALE_A,
				Instant.parse("2026-10-24T02:30:00Z"), Instant.parse("2026-10-24T03:30:00Z"), Optional.empty());
		List<SurveyAppointment> touching = appointments.findOverlapping(SALE_A, Instant.parse("2026-10-24T03:00:00Z"),
				Instant.parse("2026-10-24T04:00:00Z"), Optional.empty());
		List<SurveyAppointment> excluded = appointments.findOverlapping(SALE_A,
				Instant.parse("2026-10-24T02:30:00Z"), Instant.parse("2026-10-24T03:30:00Z"),
				Optional.of(nine.getId()));

		assertEquals(List.of(nine.getId()), overlapping.stream().map(SurveyAppointment::getId).toList());
		assertEquals(List.of(), touching);
		assertEquals(List.of(), excluded);
	}

	// ==================== resumptions and access logs ====================

	@Test
	void resumptionsAndAccessLogs_roundTripOldestFirst() {
		SurveyAppointment visit = visited(LEAD_1, 1, START);
		Basis basis = Basis.of("basis", LocalDate.parse("2026-10-25"), "Owner wants to continue");
		resumptions.save(SurveyResumption.create(8001L, LEAD_1, visit.getId(), "Owner is back", basis, LEADER,
				Instant.parse("2026-10-25T03:00:00.123456Z")));
		accessLogs.save(SurveyAccessLog.create(8102L, LEAD_1, SALE_A, Instant.parse("2026-10-25T04:00:00Z")));
		accessLogs.save(SurveyAccessLog.create(8101L, LEAD_1, LEADER, Instant.parse("2026-10-25T03:00:00Z")));
		accessLogs.save(SurveyAccessLog.create(8103L, LEAD_2, LEADER, Instant.parse("2026-10-25T03:00:00Z")));

		List<SurveyResumption> stored = resumptions.findByLeadIdOrderByOccurredAt(LEAD_1);
		assertEquals(1, stored.size());
		assertEquals(visit.getId(), stored.get(0).visitAppointmentId());
		assertEquals("Owner is back", stored.get(0).reason());
		assertEquals(basis, stored.get(0).basis());
		assertEquals(LEADER, stored.get(0).actorUserId());
		assertEquals(Instant.parse("2026-10-25T03:00:00.123456Z"), stored.get(0).occurredAt());
		assertEquals(List.of(LEADER, SALE_A), accessLogs.findByLeadId(LEAD_1).stream()
				.map(SurveyAccessLog::userId).toList());
	}

	// ==================== helpers ====================

	private static LeadLocationSnapshot leadPlace() {
		return new LeadLocationSnapshot("12 Bạch Đằng", "48", "Đà Nẵng", "20194", "Hải Châu",
				new Coordinates(new BigDecimal("16.054407"), new BigDecimal("108.202167")));
	}

	private static MeetingLocation cafe() {
		return MeetingLocation.of("Cafe 3B, 14 Bạch Đằng", "48", "Đà Nẵng", "20194", "Hải Châu",
				new Coordinates(new BigDecimal("16.054500"), new BigDecimal("108.202300")));
	}

	private static AppointmentDetails details(Instant start, int duration) {
		return AppointmentDetails.of(start, duration, MeetingLocation.fromLead(leadPlace()), null, null, null);
	}

	private SurveyAppointment booked(String leadId, int no, Instant start, String surveyor) {
		SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId, no,
				AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, leadPlace(), details(start, 60),
				null, surveyor, false, null, surveyor, BOOKED_AT);
		appointments.save(appointment);
		return appointment;
	}

	/** A scheduled appointment booked one minute before its start, so any start can be stored. */
	private SurveyAppointment bookedAt(String leadId, Instant start, int duration, String surveyor) {
		SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId, 1,
				AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, leadPlace(),
				details(start, duration), null, surveyor, false, null, surveyor, start.minusSeconds(60));
		appointments.save(appointment);
		return appointment;
	}

	private SurveyAppointment visited(String leadId, int no, Instant start) {
		SurveyAppointment appointment = SurveyAppointment.book(SurveyAppointmentId.generate(), leadId, no,
				AppointmentKind.FIRST, null, null, SurveyIntent.LONG_TERM_RENT, 1, leadPlace(), details(start, 60),
				null, SALE_A, false, null, SALE_A, start.minusSeconds(3600));
		appointment.recordVisit(start, "Visited", SALE_A, SALE_A, VisitRecordKind.OWN, null, null,
				start.plusSeconds(600));
		appointments.save(appointment);
		return appointment;
	}

	private static List<SurveyAppointmentId> ids(PageResult<SurveyAppointment> page) {
		return page.items().stream().map(SurveyAppointment::getId).toList();
	}

	private void assertRejected(String sql, String constraint) {
		DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
				() -> jdbc.update(sql));
		assertTrue(thrown.getMessage().contains(constraint), "expected constraint " + constraint + ": "
				+ thrown.getMessage());
	}
}
