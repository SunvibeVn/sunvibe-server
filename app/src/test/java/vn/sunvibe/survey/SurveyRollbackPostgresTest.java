package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.acquisition.core.application.shared.external.survey.LeadSurveyPort;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.integration.SurveyLeadEventsAdapter;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyAppointment;
import vn.sunvibe.survey.core.domain.repository.appointment.SurveyAppointmentDomainRepository;
import vn.sunvibe.survey.core.infrastructure.persistence.adapter.appointment.SurveyAppointmentRepositoryAdapter;

/**
 * A failure in the second module rolls back the first, in both directions (survey-schedule BR-2, spec 5.5; W15-28):
 * a survey command whose lead move succeeded but whose appointment save fails, and a lead command whose survey hook
 * changed the appointment and then fails. The doubles delegate to the real beans, then throw, only for the lead the
 * test names. Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, SurveyRollbackPostgresTest.FailingDoubles.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurveyRollbackPostgresTest {

	private static final String LEADS = SurveyApiTestSupport.LEADS;

	/** The lead whose appointment save fails after the real save. */
	static final AtomicReference<String> FAILING_SAVE_LEAD = new AtomicReference<>();
	/** The lead whose survey hook fails after the real hook. */
	static final AtomicReference<String> FAILING_HOOK_LEAD = new AtomicReference<>();

	@TestConfiguration
	static class FailingDoubles {

		@Bean
		@Primary
		SurveyAppointmentDomainRepository failingAppointmentSave(SurveyAppointmentRepositoryAdapter real) {
			return (SurveyAppointmentDomainRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { SurveyAppointmentDomainRepository.class }, (proxy, method, args) -> {
						Object answer = invoke(real, method, args);
						if (method.getName().equals("save")
								&& ((SurveyAppointment) args[0]).getLeadId().equals(FAILING_SAVE_LEAD.get())) {
							throw new IllegalStateException("Test double: the appointment save failed.");
						}
						return answer;
					});
		}

		@Bean
		@Primary
		LeadSurveyPort failingLeadSurveyHook(SurveyLeadEventsAdapter real) {
			return (LeadSurveyPort) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { LeadSurveyPort.class }, (proxy, method, args) -> {
						Object answer = invoke(real, method, args);
						if (args != null && args.length > 0 && args[0].equals(FAILING_HOOK_LEAD.get())) {
							throw new IllegalStateException("Test double: the survey hook failed.");
						}
						return answer;
					});
		}

		private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
			try {
				return method.invoke(target, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		}
	}

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
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0978");
		sale = survey.user("Rollback Sale", "SALE");
		otherSale = survey.user("Rollback Other Sale", "SALE");
		leader = survey.user("Rollback Leader", "SALE_LEADER");
	}

	@AfterEach
	void stopFailing() {
		FAILING_SAVE_LEAD.set(null);
		FAILING_HOOK_LEAD.set(null);
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	/** Survey command → acquisition move: the lead move made before the failed appointment save is undone. */
	@Test
	void booking_appointmentSaveFails_leavesTheLeadAndItsHistoryUnchanged() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		long version = survey.version(leadId);
		int history = historyRows(leadId);
		FAILING_SAVE_LEAD.set(leadId);

		Result result = survey.book(sale, leadId, survey.booking(leadId, SurveyApiTestSupport.startIn(24)));

		assertEquals(500, result.status(), result.rawBody());
		assertEquals("INTERNAL_ERROR", result.body().path("code").asString());
		assertEquals(version, survey.version(leadId));
		assertEquals("CONTACTED", survey.leadStatus(leadId));
		assertEquals(history, historyRows(leadId));
		assertEquals(0, count("select count(*) from survey_appointments where lead_id = ?", leadId));
		assertEquals(0, survey.accessLogRows(leadId));
	}

	/** Acquisition command → survey hook: the reassignment and the moved appointment are both undone. */
	@Test
	void reassign_surveyHookFails_leavesTheAssigneeAndTheAppointmentUnchanged() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String appointmentId = survey.booked(sale, leadId, SurveyApiTestSupport.startIn(24));
		long version = survey.version(leadId);
		long appointmentVersion = survey.appointmentVersion(appointmentId);
		int history = historyRows(leadId);
		FAILING_HOOK_LEAD.set(leadId);

		Result result = api.post(leader, LEADS + "/" + leadId + "/assign", Map.of("assigneeUserId",
				otherSale.userId(), "expectedVersion", version));

		assertEquals(500, result.status(), result.rawBody());
		assertEquals(version, survey.version(leadId));
		assertEquals(sale.userId(), jdbc.queryForObject("select assigned_user_id from owner_leads where id = ?",
				String.class, leadId));
		assertEquals(history, historyRows(leadId));
		assertEquals(appointmentVersion, survey.appointmentVersion(appointmentId));
		assertEquals(sale.userId(), jdbc.queryForObject("select surveyor_user_id from survey_appointments where id = ?",
				String.class, appointmentId));
		assertEquals(0, count("select count(*) from survey_appointment_events where appointment_id = ? and type = "
				+ "'SURVEYOR_CHANGED'", appointmentId));
	}

	/**
	 * acquisition-owner-lead-bulk-assign AC-11, BR-5: a bulk assignment whose survey hook fails on the lead written last
	 * (the highest id), after the NEW lead was assigned and the other held lead was reassigned with its appointment
	 * moved, answers 500 and leaves every lead and both appointments as they were. The ids are checked to sort in
	 * creation order; a failed assumption fails the test, it never weakens it.
	 */
	@Test
	void assignLeads_surveyHookFailsOnALaterLead_leavesEveryLeadUnchanged() throws Exception {
		String fresh = survey.lead(leader, null, "NEW");
		String held = survey.lead(leader, sale, "CONTACTED");
		String heldAppointment = survey.booked(sale, held, SurveyApiTestSupport.startIn(24));
		String failing = survey.lead(leader, sale, "CONTACTED");
		String failingAppointment = survey.booked(sale, failing, SurveyApiTestSupport.startIn(30));
		List<String> created = List.of(fresh, held, failing);
		assertEquals(created.stream().sorted().toList(), created, "the lead ids sort in creation order");
		Map<String, Map<String, Object>> leadsBefore = new LinkedHashMap<>();
		for (String leadId : created) {
			leadsBefore.put(leadId, leadRow(leadId));
		}
		Map<String, Map<String, Object>> appointmentsBefore = new LinkedHashMap<>();
		for (String appointmentId : List.of(heldAppointment, failingAppointment)) {
			appointmentsBefore.put(appointmentId, appointmentRow(appointmentId));
		}
		FAILING_HOOK_LEAD.set(failing);

		Result result = api.post(leader, LEADS + "/assignments", Map.of("assigneeUserId", otherSale.userId(), "leads",
				List.of(item(failing), item(fresh), item(held))));

		assertEquals(500, result.status(), result.rawBody());
		assertEquals("INTERNAL_ERROR", result.body().path("code").asString());
		for (String leadId : created) {
			assertEquals(leadsBefore.get(leadId), leadRow(leadId), leadId);
		}
		for (String appointmentId : List.of(heldAppointment, failingAppointment)) {
			assertEquals(appointmentsBefore.get(appointmentId), appointmentRow(appointmentId), appointmentId);
			assertEquals(0, count("select count(*) from survey_appointment_events where appointment_id = ? and type = "
					+ "'SURVEYOR_CHANGED'", appointmentId));
		}
		assertEquals(0, count("select count(*) from owner_lead_access_logs where lead_id in (?, ?, ?)", fresh, held,
				failing));
	}

	private Map<String, Object> item(String leadId) {
		return Map.of("leadId", leadId, "expectedVersion", survey.version(leadId));
	}

	/** Status, assignee, version and History count of the lead. */
	private Map<String, Object> leadRow(String leadId) {
		return jdbc.queryForMap("select l.status, l.assigned_user_id, l.version, (select count(*) from "
				+ "owner_lead_activities a where a.lead_id = l.id) as history from owner_leads l where l.id = ?", leadId);
	}

	/** Surveyor, status, confirmation and version of the appointment. */
	private Map<String, Object> appointmentRow(String appointmentId) {
		return jdbc.queryForMap("select surveyor_user_id, status, confirmation_state, version from survey_appointments "
				+ "where id = ?", appointmentId);
	}

	/** Acquisition command → survey hook: the hold and the cancelled appointment are both undone. */
	@Test
	void hold_surveyHookFails_leavesTheLeadScheduledAndTheAppointmentScheduled() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String appointmentId = survey.booked(sale, leadId, SurveyApiTestSupport.startIn(24));
		long version = survey.version(leadId);
		int history = historyRows(leadId);
		FAILING_HOOK_LEAD.set(leadId);

		Result result = survey.moveTo(sale, leadId, "ON_HOLD");

		assertEquals(500, result.status(), result.rawBody());
		assertEquals(version, survey.version(leadId));
		assertEquals("SURVEY_SCHEDULED", survey.leadStatus(leadId));
		assertEquals(history, historyRows(leadId));
		assertEquals("SCHEDULED", jdbc.queryForObject("select status from survey_appointments where id = ?",
				String.class, appointmentId));
	}

	private int historyRows(String leadId) {
		return count("select count(*) from owner_lead_activities where lead_id = ?", leadId);
	}

	private int count(String sql, Object... args) {
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count == null ? 0 : count;
	}
}
