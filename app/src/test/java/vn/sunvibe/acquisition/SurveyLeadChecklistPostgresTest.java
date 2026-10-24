package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistActivity;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistActivityResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistEvent;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadId;
import vn.sunvibe.acquisition.core.domain.repository.OwnerLeadActivityDomainRepository;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * What acquisition gives the survey checklist on PostgreSQL (survey-checklist spec 5.3, A-5, A-25): the visible lead
 * read under a {@code FOR SHARE} row lock that lead commands wait for, and a History entry written as an activity row
 * only, so the lead's version and status never change while LAST UPDATE moves. Leads come in through the intake API.
 * Needs Docker.
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
class SurveyLeadChecklistPostgresTest {

	private static final String LOCK_NOT_AVAILABLE = "55P03";
	private static final String CHECKLIST = "01JCHECKLISTPGAAAAAAAAAAAA";

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

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private SurveyLeadUseCase surveyLeads;

	@Autowired
	private OwnerLeadActivityDomainRepository activities;

	private SurveyApiTestSupport survey;
	private TestUser sale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0979");
		sale = survey.user("Share Sale", "SALE");
		leader = survey.user("Share Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	/** spec 5.3: the read takes FOR SHARE in the caller's transaction, so a lead command (FOR NO KEY UPDATE) waits. */
	@Test
	void findVisibleForShare_holdsShareLock() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		long version = survey.version(leadId);

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			assertTrue(surveyLeads.findVisibleForShare(leadId, viewer(sale)).isPresent());
			int holder = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
			try (Connection other = dataSource.getConnection()) {
				other.setAutoCommit(false);
				assertTrue(probe(other, "select 1 from owner_leads where id = ? for share nowait", leadId),
						"a second share lock must be granted");
				SQLException refused = assertThrows(SQLException.class,
						() -> probe(other, "select 1 from owner_leads where id = ? for no key update nowait", leadId));
				assertEquals(LOCK_NOT_AVAILABLE, refused.getSQLState());
				other.rollback();
			} catch (SQLException e) {
				throw new IllegalStateException(e);
			}
			assertEquals(1, jdbc.queryForObject("select count(*) from pg_locks l join pg_class c on c.oid = l.relation"
					+ " where c.relname = 'owner_leads' and l.mode = 'RowShareLock' and l.pid = ?", Integer.class,
					holder));
		});

		assertEquals(version, survey.version(leadId));
	}

	@Test
	void findVisibleForShare_outsideTransaction_isRefused() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");

		assertThrows(IllegalTransactionStateException.class,
				() -> surveyLeads.findVisibleForShare(leadId, viewer(sale)));
	}

	/** spec 4.9, I-5: one activity row; the lead keeps its version and status; LAST UPDATE is that row's time. */
	@Test
	void recordChecklistActivity_keepsLeadVersion_movesLastActivity() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		long version = survey.version(leadId);
		String status = survey.leadStatus(leadId);
		int rows = historyRows(leadId);
		Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);

		SurveyChecklistActivityResult result = new TransactionTemplate(transactionManager).execute(tx -> {
			surveyLeads.findVisibleForShare(leadId, viewer(sale)).orElseThrow();
			return surveyLeads.recordChecklistActivity(leadId, new SurveyChecklistActivity(
					SurveyChecklistEvent.CHECKLIST_SAVED, sale.userId(), CHECKLIST, 4, "TERMS", at));
		});

		assertEquals(version, survey.version(leadId));
		assertEquals(status, survey.leadStatus(leadId));
		assertEquals(rows + 1, historyRows(leadId));
		Map<String, Object> row = jdbc.queryForMap("select type, actor_user_id, reference_id, reference_no,"
				+ " reference_detail, note, from_status, to_status from owner_lead_activities"
				+ " where lead_id = ? and type = 'CHECKLIST_SAVED'", leadId);
		assertEquals(sale.userId(), row.get("actor_user_id"));
		assertEquals(CHECKLIST, row.get("reference_id"));
		assertEquals(4, row.get("reference_no"));
		assertEquals("TERMS", row.get("reference_detail"));
		assertEquals(null, row.get("note"));
		assertEquals(null, row.get("to_status"));
		assertEquals(at, result.lastActivityAt());
		assertEquals(at, result.entry().occurredAt());
		assertEquals("Share Sale", result.entry().actorName());
		assertEquals("TERMS", result.entry().referenceDetail());
	}

	/** spec A-25: the staff ids named on the lead's History (actor, previous and new assignee), one query. */
	@Test
	void findStaffIds_listsActorsAndAssignees() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String otherLead = survey.lead(leader, leader, "ASSIGNED");

		assertEquals(Set.of(leader.userId(), sale.userId()), activities.findStaffIds(OwnerLeadId.of(leadId)));
		assertEquals(Set.of(leader.userId()), activities.findStaffIds(OwnerLeadId.of(otherLead)));
		Set<String> staff = new TransactionTemplate(transactionManager).execute(tx -> {
			surveyLeads.findVisibleForShare(leadId, new SurveyLeadViewer(leader.userId(), SurveyLeadAccess.ALL))
					.orElseThrow();
			return surveyLeads.leadStaffIds(leadId);
		});
		assertEquals(Set.of(leader.userId(), sale.userId()), staff);
	}

	private static SurveyLeadViewer viewer(TestUser user) {
		return new SurveyLeadViewer(user.userId(), SurveyLeadAccess.OWN);
	}

	private int historyRows(String leadId) {
		return jdbc.queryForObject("select count(*) from owner_lead_activities where lead_id = ?", Integer.class,
				leadId);
	}

	private static boolean probe(Connection connection, String sql, String leadId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, leadId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}
}
