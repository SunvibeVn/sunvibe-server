package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
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
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;

/**
 * A create needs one pooled connection (survey-media plan Tasks A5, A13): the intake gate's health read joins the
 * command's transaction instead of taking a second connection, so with a Hikari pool of one the create completes. Its
 * own context (the pool size is a property), with its own database. Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			"spring.datasource.hikari.maximum-pool-size=1",
			"spring.datasource.hikari.connection-timeout=5000" })
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, MediaTestSwitches.Config.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaCreatePoolOfOnePostgresTest {

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
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0974");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Pool Of One Sale", "SALE");
		leader = survey.user("Pool Of One Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	@Test
	@Timeout(120)
	void create_withHikariPoolOfOne_completesThroughGate() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String checklistId = checklists.started(sale, visit.appointmentId());
		MediaIntakeFixture.open(jdbc);

		Result created = media.create(sale, checklistId, MediaApiTestSupport.exterior(MediaApiTestSupport.sha(
				"pool-of-one")));

		assertEquals(201, created.status(), created.rawBody());
	}
}
