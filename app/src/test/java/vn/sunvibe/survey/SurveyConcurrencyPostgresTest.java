package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * Concurrent commands on one lead across acquisition and survey on PostgreSQL (survey-schedule spec 5.5; W15-4,
 * W15-5). Both directions write the lead row first, so two commands of one lead meet on it: one answers 2xx, the other
 * 409 CONCURRENT_MODIFICATION on the version it sent (never a 500 or a deadlock), and only the winner leaves a History
 * row. The invariants I-1..I-4 of spec 4.4 hold afterwards. Each case starts both HTTP calls at one gate, 5 rounds
 * with fresh leads. Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurveyConcurrencyPostgresTest {

	private static final String LEADS = SurveyApiTestSupport.LEADS;
	private static final String APPOINTMENTS = SurveyApiTestSupport.APPOINTMENTS;
	private static final int ROUNDS = 5;

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private JdbcTemplate jdbc;

	private SurveyApiTestSupport survey;
	private AcquisitionApiTestSupport api;
	private ExecutorService executor;
	private final List<String> leadIds = new ArrayList<>();
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0977");
		executor = Executors.newFixedThreadPool(2);
		sale = survey.user("Race Sale", "SALE");
		otherSale = survey.user("Race Other Sale", "SALE");
		leader = survey.user("Race Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		executor.shutdownNow();
		survey.cleanUp();
	}

	/** W15-4, Review Focus 5: a double click on Save gives one appointment and a clean 409. */
	@Test
	void concurrentBookings_oneWins() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			String leadId = lead("CONTACTED");
			long version = survey.version(leadId);
			int history = historyRows(leadId);

			List<Result> results = race(i -> survey.book(sale, leadId, bookingAt(leadId, version,
					SurveyApiTestSupport.startIn(24 + 2 * i))));

			String context = "round " + round + ": " + statuses(results);
			int winner = winnerOf(results, 201, context);
			Result loser = results.get(1 - winner);
			String code = loser.body().path("code").asString();
			assertTrue(Set.of("CONCURRENT_MODIFICATION", "SURVEY_ALREADY_SCHEDULED").contains(code),
					context + ", " + loser.rawBody());
			if (code.equals("CONCURRENT_MODIFICATION")) {
				assertEquals("expectedLeadVersion", field(loser), context);
			}
			assertEquals(1, count("select count(*) from survey_appointments where lead_id = ? and status = "
					+ "'SCHEDULED'", leadId), context);
			assertEquals(1, count("select count(*) from survey_appointments where lead_id = ?", leadId), context);
			assertEquals(history + 1, historyRows(leadId), context);
			assertEquals(1, count("select count(*) from owner_lead_activities where lead_id = ? and type = "
					+ "'SURVEY_BOOKED'", leadId), context);
			assertEquals(version + 1, survey.version(leadId), context);
		}
		assertInvariants();
	}

	/** W15-5: holding the lead while its appointment is being changed. */
	@Test
	void holdVersusChange_oneWins() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			Booked booked = booked();
			Instant later = booked.start().plus(Duration.ofHours(1));

			List<Result> results = race(i -> i == 0
					? api.post(sale, LEADS + "/" + booked.leadId() + "/status-changes", Map.of("toStatus", "ON_HOLD",
							"expectedVersion", booked.leadVersion()))
					: api.post(sale, APPOINTMENTS + "/" + booked.id() + "/changes", change(booked, later)));

			assertPairOutcome(booked, results, "round " + round);
		}
		assertInvariants();
	}

	/** W15-5: giving the lead to someone else while its appointment is being confirmed. */
	@Test
	void reassignVersusConfirm_oneWins() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			Booked booked = booked();

			List<Result> results = race(i -> i == 0
					? api.post(leader, LEADS + "/" + booked.leadId() + "/assign", Map.of("assigneeUserId",
							otherSale.userId(), "expectedVersion", booked.leadVersion()))
					: api.post(leader, APPOINTMENTS + "/" + booked.id() + "/confirmation", Map.of(
							"expectedLeadVersion", booked.leadVersion(), "expectedAppointmentVersion",
							booked.appointmentVersion(), "confirmed", true)));

			assertPairOutcome(booked, results, "round " + round);
		}
		assertInvariants();
	}

	/** W15-5: booking while the requirement is being changed. */
	@Test
	void bookVersusRequirementChange_oneWins() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			String leadId = lead("CONTACTED");
			long version = survey.version(leadId);
			Booked lead = new Booked(leadId, null, null, version, -1, historyRows(leadId));

			List<Result> results = race(i -> i == 0
					? api.post(sale, LEADS + "/" + leadId + "/intent-changes", intentChange(version))
					: survey.book(sale, leadId, bookingAt(leadId, version, SurveyApiTestSupport.startIn(24))));

			assertPairOutcome(lead, results, "round " + round);
			int scheduled = count("select count(*) from survey_appointments where lead_id = ? and status = "
					+ "'SCHEDULED'", leadId);
			assertEquals(results.get(1).status() == 201 ? 1 : 0, scheduled, "round " + round);
		}
		assertInvariants();
	}

	/** W15-5: recording the visit of an overdue appointment while the lead is put on hold. */
	@Test
	void visitVersusHold_oneWins() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			Booked fresh = booked();
			survey.shiftBack(fresh.id(), Duration.ofDays(2));
			Booked booked = new Booked(fresh.leadId(), fresh.id(), fresh.start().minus(Duration.ofDays(2)),
					fresh.leadVersion(), fresh.appointmentVersion(), fresh.historyRows());

			List<Result> results = race(i -> i == 0
					? api.post(sale, LEADS + "/" + booked.leadId() + "/status-changes", Map.of("toStatus", "ON_HOLD",
							"expectedVersion", booked.leadVersion()))
					: api.post(sale, APPOINTMENTS + "/" + booked.id() + "/visit", visit(booked)));

			assertPairOutcome(booked, results, "round " + round);
		}
		assertInvariants();
	}

	// ==================== checks ====================

	/**
	 * The first call is an acquisition command (409 field {@code expectedVersion}), the second a survey command (409
	 * field {@code expectedLeadVersion} or {@code expectedAppointmentVersion}); exactly one wins, the lead moved one
	 * version and has one new History row.
	 */
	private void assertPairOutcome(Booked before, List<Result> results, String round) {
		String context = round + ": " + statuses(results);
		int winner = winnerOf(results, null, context);
		Result loser = results.get(1 - winner);
		assertEquals("CONCURRENT_MODIFICATION", loser.body().path("code").asString(), context + ", " + loser.rawBody());
		if (winner == 1) {
			assertEquals("expectedVersion", field(loser), context);
		} else {
			assertTrue(Set.of("expectedLeadVersion", "expectedAppointmentVersion").contains(field(loser)),
					context + ", " + loser.rawBody());
		}
		assertEquals(before.leadVersion() + 1, survey.version(before.leadId()), context);
		assertEquals(before.historyRows() + 1, historyRows(before.leadId()), context);
	}

	/**
	 * Exactly one 2xx (the given status, or 200/201 when null) and one 409, never anything else.
	 *
	 * @return the index of the call that won
	 */
	private static int winnerOf(List<Result> results, Integer okStatus, String context) {
		Set<Integer> ok = okStatus == null ? Set.of(200, 201) : Set.of(okStatus);
		for (Result result : results) {
			assertTrue(ok.contains(result.status()) || result.status() == 409, context + ", " + result.rawBody());
		}
		assertEquals(1, results.stream().filter(r -> ok.contains(r.status())).count(), context);
		assertEquals(1, results.stream().filter(r -> r.status() == 409).count(), context);
		return ok.contains(results.get(0).status()) ? 0 : 1;
	}

	/** Spec 4.4 I-1..I-4 over the leads of this class. */
	private void assertInvariants() {
		String leads = "('" + String.join("','", leadIds) + "')";
		assertEquals(0, count("select count(*) from (select lead_id from survey_appointments where status = "
				+ "'SCHEDULED' and lead_id in " + leads + " group by lead_id having count(*) > 1) twice"), "I-1");
		assertEquals(0, count("select count(*) from survey_appointments a join owner_leads l on l.id = a.lead_id "
				+ "where a.status = 'SCHEDULED' and l.status <> 'SURVEY_SCHEDULED' and l.id in " + leads), "I-2");
		assertEquals(0, count("select count(*) from owner_leads l where l.status = 'SURVEY_SCHEDULED' and l.id in "
				+ leads + " and not exists (select 1 from survey_appointments a where a.lead_id = l.id and "
				+ "(a.status = 'SCHEDULED' or (a.status = 'VISITED' and a.intent_cycle = l.intent_cycle)))"), "I-3");
		assertEquals(0, count("select count(*) from survey_appointments a join owner_leads l on l.id = a.lead_id "
				+ "where a.status = 'SCHEDULED' and a.surveyor_user_id <> l.assigned_user_id and l.id in " + leads),
				"I-4");
	}

	// ==================== helpers ====================

	@FunctionalInterface
	private interface Call {
		Result send(int index) throws Exception;
	}

	/** Starts two calls at one gate and waits for both; no fixed sleeps. */
	private List<Result> race(Call call) throws Exception {
		int parallel = 2;
		CountDownLatch ready = new CountDownLatch(parallel);
		CountDownLatch gate = new CountDownLatch(1);
		List<CompletableFuture<Result>> futures = new ArrayList<>();
		for (int i = 0; i < parallel; i++) {
			int index = i;
			futures.add(CompletableFuture.supplyAsync(() -> {
				ready.countDown();
				try {
					gate.await();
					return call.send(index);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}, executor));
		}
		assertTrue(ready.await(30, TimeUnit.SECONDS), "workers did not start");
		gate.countDown();
		List<Result> results = new ArrayList<>();
		for (CompletableFuture<Result> future : futures) {
			results.add(future.get(60, TimeUnit.SECONDS));
		}
		return results;
	}

	/** A scheduled appointment as the race sees it: both versions and the lead's History rows before the race. */
	private record Booked(String leadId, String id, Instant start, long leadVersion, long appointmentVersion,
			int historyRows) {
	}

	private String lead(String status) throws Exception {
		String leadId = survey.lead(leader, sale, status);
		leadIds.add(leadId);
		return leadId;
	}

	/** A CONTACTED lead of the sale with an appointment booked by them at the next day's same hour. */
	private Booked booked() throws Exception {
		String leadId = lead("CONTACTED");
		Instant start = SurveyApiTestSupport.startIn(24);
		String id = survey.booked(sale, leadId, start);
		return new Booked(leadId, id, start, survey.version(leadId), survey.appointmentVersion(id),
				historyRows(leadId));
	}

	private Map<String, Object> bookingAt(String leadId, long version, Instant start) {
		Map<String, Object> body = survey.booking(leadId, start);
		body.put("expectedLeadVersion", version);
		return body;
	}

	private Map<String, Object> change(Booked booked, Instant start) {
		Map<String, Object> row = jdbc.queryForMap("select address_detail, province_code, ward_code, latitude, "
				+ "longitude from owner_leads where id = ?", booked.leadId());
		Map<String, Object> place = new LinkedHashMap<>();
		place.put("addressText", row.get("address_detail"));
		place.put("provinceCode", row.get("province_code"));
		place.put("wardCode", row.get("ward_code"));
		place.put("latitude", row.get("latitude"));
		place.put("longitude", row.get("longitude"));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", booked.leadVersion());
		body.put("expectedAppointmentVersion", booked.appointmentVersion());
		body.put("reason", "Owner asked for later");
		body.put("startAt", start.toString());
		body.put("durationMinutes", 60);
		body.put("meetingLocation", place);
		body.put("accessNote", null);
		body.put("onSiteContactName", null);
		body.put("onSiteContactRole", null);
		return body;
	}

	private Map<String, Object> visit(Booked booked) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", booked.leadVersion());
		body.put("expectedAppointmentVersion", booked.appointmentVersion());
		body.put("arrivedAt", booked.start().toString());
		body.put("note", "Met the owner");
		body.put("performedByUserId", sale.userId());
		return body;
	}

	private static Map<String, Object> intentChange(long version) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", version);
		body.put("newIntent", "SHORT_TERM_RENT");
		body.put("askingPrice", null);
		body.put("reason", "Owner prefers short stays");
		body.put("basis", Map.of("confirmedOn", LocalDate.now(SurveyTimeRules.ZONE).toString(), "text",
				"Owner said so"));
		return body;
	}

	private int historyRows(String leadId) {
		return count("select count(*) from owner_lead_activities where lead_id = ?", leadId);
	}

	private static String field(Result result) {
		return result.body().path("errors").path(0).path("field").asString();
	}

	private static List<Integer> statuses(List<Result> results) {
		return results.stream().map(Result::status).toList();
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}
}
