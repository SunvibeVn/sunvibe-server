package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.integration.AcquisitionSurveyLeadAdapter;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyLeadPort;

/**
 * A checklist command is one transaction (survey-checklist spec 5.5): when its History insert, the last write, fails,
 * nothing of the command stays. The double delegates to the real port, then throws, only for the lead the test names.
 * Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import({ PostgresContainerConfig.class, ChecklistRollbackPostgresTest.FailingDoubles.class })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// The doubles make a context (and a PostgreSQL container) no other class reuses: close it after the class so the
// cached contexts of the suite fit in the test JVM
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChecklistRollbackPostgresTest {

	/** The lead whose checklist History insert fails after the real insert. */
	static final AtomicReference<String> FAILING_LEAD = new AtomicReference<>();

	@TestConfiguration
	static class FailingDoubles {

		@Bean
		@Primary
		SurveyLeadPort failingChecklistActivity(AcquisitionSurveyLeadAdapter real) {
			return (SurveyLeadPort) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { SurveyLeadPort.class }, (proxy, method, args) -> {
						Object answer = invoke(real, method, args);
						if (method.getName().equals("recordChecklistActivity")
								&& args[0].equals(FAILING_LEAD.get())) {
							throw new IllegalStateException("Test double: the History insert failed.");
						}
						return answer;
					});
		}

		private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
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
	private ChecklistApiTestSupport checklists;
	private TestUser sale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0969");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		sale = survey.user("Checklist Rollback Sale", "SALE");
		leader = survey.user("Checklist Rollback Leader", "SALE_LEADER");
	}

	@AfterEach
	void stopFailing() {
		FAILING_LEAD.set(null);
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	@Test
	void start_historyInsertFails_leavesNoChecklistRowAndNoAccessLogRow() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		int logs = checklists.accessLogs(visit.leadId()).size();
		int history = historyRows(visit.leadId());
		FAILING_LEAD.set(visit.leadId());

		Result result = checklists.start(sale, visit.appointmentId(), null);

		assertEquals(500, result.status(), result.rawBody());
		assertEquals("INTERNAL_ERROR", result.body().path("code").asString());
		assertEquals(0, checklists.rows("select count(*) from survey_checklists where lead_id = ?", visit.leadId()));
		assertEquals(0, checklists.rows("select count(*) from survey_checklist_groups g join survey_checklists c on "
				+ "c.id = g.checklist_id where c.lead_id = ?", visit.leadId()));
		assertEquals(0, checklists.rows("select count(*) from survey_checklist_takeovers t join survey_checklists c on "
				+ "c.id = t.checklist_id where c.lead_id = ?", visit.leadId()));
		assertEquals(logs, checklists.accessLogs(visit.leadId()).size());
		assertEquals(history, historyRows(visit.leadId()));
	}

	@Test
	void save_historyInsertFails_keepsTheGroupVersionAndWritesNoSaveRow() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		String spaceId = checklists.newId();
		checklists.saved(sale, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "P101"))));
		int saves = checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ?", id);
		int logs = checklists.accessLogs(visit.leadId()).size();
		FAILING_LEAD.set(visit.leadId());

		Result result = checklists.save(sale, id, "parts", c -> set(c, "accessRoute", checklists.value("Alley")));

		assertEquals(500, result.status(), result.rawBody());
		assertEquals(0L, jdbc.queryForObject("select version from survey_checklist_groups where checklist_id = ? and "
				+ "group_key = 'PARTS'", Long.class, id));
		assertEquals(saves, checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ?", id));
		assertEquals(logs + 1, checklists.accessLogs(visit.leadId()).size(), "only the read before the save");
	}

	private int historyRows(String leadId) {
		return checklists.rows("select count(*) from owner_lead_activities where lead_id = ?", leadId);
	}
}
