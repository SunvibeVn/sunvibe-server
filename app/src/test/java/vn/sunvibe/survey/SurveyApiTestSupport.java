package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;

/**
 * Shared helpers of the survey API tests (end to end, real security chain). Leads come in through the public intake
 * form in Hải Châu (no territory, so they start NEW) and are moved with the staff lead endpoints, so they carry the
 * same history as real leads. Appointments are booked through the survey API; {@link #shiftBack} moves one into the
 * past by rewriting its times with JDBC, since the server clock is real. Everything made here is removed by
 * {@link #cleanUp}.
 */
public final class SurveyApiTestSupport {

	public static final String LEADS = "/api/v1/staff/owner-leads";
	public static final String APPOINTMENTS = "/api/v1/staff/survey-appointments";
	/** Hải Châu: in the catalog, no territory in the survey tests. */
	public static final String WARD = "20242";
	/** Hòa Cường: another ward of Đà Nẵng. */
	public static final String OTHER_WARD = "20257";
	/** Hoàng Sa: the special-zone ward, where no pin is accepted. */
	public static final String SPECIAL_ZONE_WARD = "20333";

	private final AcquisitionApiTestSupport api;
	private final JdbcTemplate jdbc;
	private final AtomicInteger sequence;
	private final List<String> leadIds = new ArrayList<>();
	private final List<String> userIds = new ArrayList<>();

	/**
	 * @param phonePrefix four digits that keep this test class's phones apart from the others, for example "0972"
	 */
	public SurveyApiTestSupport(AcquisitionApiTestSupport api, JdbcTemplate jdbc, String phonePrefix) {
		this.api = api;
		this.jdbc = jdbc;
		this.sequence = new AtomicInteger(Integer.parseInt(phonePrefix) * 1_000_000);
	}

	public AcquisitionApiTestSupport api() {
		return api;
	}

	/** A logged-in user with these roles and a full name. */
	public TestUser user(String fullName, String... roles) throws Exception {
		TestUser created = api.createUser(fullName, UserStatus.ACTIVE, roles);
		userIds.add(created.userId());
		return api.logIn(created);
	}

	/**
	 * A long-term rental lead (price per month) of the assignee in the status: NEW (no assignee), ASSIGNED,
	 * CONTACTED, ON_HOLD or a closed status (REJECTED).
	 */
	public String lead(TestUser leader, TestUser assigneeOrNull, String status) throws Exception {
		return lead(leader, assigneeOrNull, status, "LONG_TERM_RENT",
				AcquisitionApiTestSupport.price(12_000_000, "MONTH"));
	}

	/** A lead of the intent and price (null: none), assigned and moved to the status. */
	public String lead(TestUser leader, TestUser assigneeOrNull, String status, String intent,
			Map<String, Object> priceOrNull) throws Exception {
		int n = sequence.incrementAndGet();
		String phone = "0" + String.format("%09d", n % 1_000_000_000);
		String code = api.submitLead(WARD, phone, n + " Bạch Đằng", intent, priceOrNull);
		String leadId = AcquisitionApiTestSupport.leadId(jdbc, code);
		leadIds.add(leadId);
		if (assigneeOrNull == null) {
			return leadId;
		}
		ok(api.post(leader, LEADS + "/" + leadId + "/assign",
				Map.of("assigneeUserId", assigneeOrNull.userId(), "expectedVersion", version(leadId))));
		switch (status) {
			case "ASSIGNED" -> {
			}
			case "CONTACTED" -> moveTo(leader, leadId, "CONTACTED");
			case "ON_HOLD" -> moveTo(leader, leadId, "ON_HOLD");
			case "REJECTED" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("toStatus", "REJECTED");
				body.put("expectedVersion", version(leadId));
				body.put("reason", "Not a fit");
				ok(api.post(leader, LEADS + "/" + leadId + "/status-changes", body));
			}
			default -> throw new IllegalArgumentException(status);
		}
		return leadId;
	}

	/** The lead's phone as the owner types it (0 and nine digits). */
	public String phone(String leadId) {
		String normalized = jdbc.queryForObject("select phone_normalized from owner_leads where id = ?", String.class,
				leadId);
		return "0" + normalized.substring(3);
	}

	/** A NEW lead submitted with the phone (kept for {@link #cleanUp}). */
	public String submitted(String phone) throws Exception {
		String code = api.submitLead(WARD, phone, sequence.incrementAndGet() + " Bạch Đằng, lần hai");
		String leadId = AcquisitionApiTestSupport.leadId(jdbc, code);
		leadIds.add(leadId);
		return leadId;
	}

	/** Moves the lead with the staff status-change endpoint. */
	public Result moveTo(TestUser caller, String leadId, String toStatus) throws Exception {
		return api.post(caller, LEADS + "/" + leadId + "/status-changes",
				Map.of("toStatus", toStatus, "expectedVersion", version(leadId)));
	}

	/** The lead's stored version. */
	public long version(String leadId) {
		return jdbc.queryForObject("select version from owner_leads where id = ?", Long.class, leadId);
	}

	public String leadStatus(String leadId) {
		return jdbc.queryForObject("select status from owner_leads where id = ?", String.class, leadId);
	}

	/** A start in whole minutes, the given hours after the next full hour (always in the future). */
	public static Instant startIn(int hours) {
		return Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1 + hours));
	}

	/** The smallest booking body: the lead's version and the start. */
	public Map<String, Object> booking(String leadId, Instant start) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedLeadVersion", version(leadId));
		body.put("startAt", start.toString());
		return body;
	}

	/** Books with the body; asserts nothing. */
	public Result book(TestUser caller, String leadId, Map<String, Object> body) throws Exception {
		return api.post(caller, LEADS + "/" + leadId + "/survey-appointments", body);
	}

	/** Books at the start and returns the new appointment's id (asserts 201). */
	public String booked(TestUser caller, String leadId, Instant start) throws Exception {
		Result result = book(caller, leadId, booking(leadId, start));
		assertEquals(201, result.status(), result.rawBody());
		return result.body().path("survey").path("appointments").path(0).path("id").asString();
	}

	/** The appointment's stored version. */
	public long appointmentVersion(String appointmentId) {
		return jdbc.queryForObject("select version from survey_appointments where id = ?", Long.class,
				appointmentId);
	}

	/**
	 * Moves one appointment into the past: start, creation, last change and confirmation times, the revisions'
	 * valid-from and start, and the events' times all go back by the same amount, so the appointment looks booked
	 * earlier. Only for tests; the server clock stays real.
	 */
	public void shiftBack(String appointmentId, Duration by) {
		long seconds = by.toSeconds();
		jdbc.update("update survey_appointments set start_at = start_at - ? * interval '1 second', "
				+ "created_at = created_at - ? * interval '1 second', updated_at = updated_at - ? * interval '1 second', "
				+ "confirmation_changed_at = confirmation_changed_at - ? * interval '1 second' where id = ?",
				seconds, seconds, seconds, seconds, appointmentId);
		jdbc.update("update survey_appointment_revisions set valid_from = valid_from - ? * interval '1 second', "
				+ "start_at = start_at - ? * interval '1 second' where appointment_id = ?", seconds, seconds,
				appointmentId);
		jdbc.update("update survey_appointment_events set occurred_at = occurred_at - ? * interval '1 second' "
				+ "where appointment_id = ?", seconds, appointmentId);
	}

	/** The survey access-log rows of the lead. */
	public int accessLogRows(String leadId) {
		return jdbc.queryForObject("select count(*) from survey_access_logs where lead_id = ?", Integer.class,
				leadId);
	}

	/** Deletes the survey rows of the leads made here, then the leads and the users. */
	public void cleanUp() {
		deleteSurveyRows(jdbc, leadIds);
		AcquisitionApiTestSupport.deleteLeads(jdbc, leadIds);
		AcquisitionApiTestSupport.deleteUsers(jdbc, userIds);
		api.http().close();
	}

	/** Deletes every survey row of the leads (logs, resumptions, events, revisions, appointments). */
	public static void deleteSurveyRows(JdbcTemplate jdbc, Collection<String> leadIds) {
		for (String id : leadIds) {
			jdbc.update("delete from survey_access_logs where lead_id = ?", id);
			jdbc.update("delete from survey_resumptions where lead_id = ?", id);
			jdbc.update("delete from survey_appointment_events where appointment_id in "
					+ "(select id from survey_appointments where lead_id = ?)", id);
			jdbc.update("delete from survey_appointment_revisions where appointment_id in "
					+ "(select id from survey_appointments where lead_id = ?)", id);
			jdbc.update("delete from survey_appointments where lead_id = ?", id);
		}
	}

	public static void ok(Result result) {
		assertEquals(200, result.status(), result.rawBody());
	}

	/** The error answer: status, code and the field of its first error (null: no error entry). */
	public static void assertError(Result result, int status, String code, String fieldOrNull) {
		assertEquals(status, result.status(), result.rawBody());
		assertEquals(code, result.body().path("code").asString(), result.rawBody());
		if (fieldOrNull == null) {
			assertEquals(0, result.body().path("errors").size(), result.rawBody());
		} else {
			assertEquals(fieldOrNull, result.body().path("errors").path(0).path("field").asString(), result.rawBody());
		}
	}

	/** The appointment with this id in a survey block answer. */
	public static JsonNode appointmentOf(JsonNode block, String id) {
		for (JsonNode appointment : block.path("appointments")) {
			if (id.equals(appointment.path("id").asString())) {
				return appointment;
			}
		}
		throw new AssertionError("no appointment " + id + " in " + block);
	}
}
