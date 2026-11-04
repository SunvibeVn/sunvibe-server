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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadEvent;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMove;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadTarget;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The locked lead read of a decision (survey-checklist submission spec 5.2, 5.4, plan PD-5) on a real PostgreSQL. The
 * row lock is {@code FOR NO KEY UPDATE}: another share or key-share reader, which a concurrent activity insert is
 * through its foreign key, is not blocked by what {@code FOR UPDATE} would block. {@code applySurveyMove} reuses the
 * locked row in the same transaction. Needs Docker, like {@code FlywayPostgresMigrationTest}.
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
class SurveyLeadLockPostgresTest {

	private static final String LOCK_NOT_AVAILABLE = "55P03";
	private static final String CHECKLIST = "01JCHECKLISTLOCKTESTAAAAAA";

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

	private SurveyApiTestSupport survey;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0982");
		sale = survey.user("Lock Sale", "SALE");
		otherSale = survey.user("Lock Other Sale", "SALE");
		leader = survey.user("Lock Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	private static SurveyLeadViewer viewer(TestUser user) {
		return new SurveyLeadViewer(user.userId(), SurveyLeadAccess.OWN);
	}

	@Test
	void findVisibleForUpdate_takesNoKeyUpdate() throws Exception {
		String leadId = scheduledLead();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			assertTrue(surveyLeads.findVisibleForUpdate(leadId, viewer(sale)).isPresent());
			int holder = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
			try (Connection other = dataSource.getConnection()) {
				other.setAutoCommit(false);
				List<String> modes = rowLockModes(other, leadId, holder);
				assertTrue(modes.contains("RowShareLock") || modes.contains("RowExclusiveLock"), modes.toString());
				// FOR NO KEY UPDATE leaves key share free; FOR UPDATE would refuse it
				assertTrue(probe(other, "select 1 from owner_leads where id = ? for key share nowait", leadId),
						"a key-share lock must be granted");
				SQLException refused = assertThrows(SQLException.class,
						() -> probe(other, "select 1 from owner_leads where id = ? for share nowait", leadId));
				assertEquals(LOCK_NOT_AVAILABLE, refused.getSQLState());
				other.rollback();
			} catch (SQLException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	/** spec 5.4: a concurrent activity insert, whose foreign key takes a key-share lock on the lead, does not wait. */
	@Test
	void findVisibleForUpdate_concurrentActivityInsert_doesNotWait() throws Exception {
		String leadId = scheduledLead();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			assertTrue(surveyLeads.findVisibleForUpdate(leadId, viewer(sale)).isPresent());
			try (Connection other = dataSource.getConnection()) {
				other.setAutoCommit(false);
				try (PreparedStatement timeout = other.prepareStatement("set local lock_timeout = '500ms'")) {
					timeout.execute();
				}
				try (PreparedStatement insert = other.prepareStatement("insert into owner_lead_activities"
						+ " (id, lead_id, type, occurred_at) values (?, ?, 'NOTE_ADDED', now())")) {
					insert.setLong(1, 987654321L);
					insert.setString(2, leadId);
					assertEquals(1, insert.executeUpdate());
				}
				other.rollback();
			} catch (SQLException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	@Test
	void findVisibleForUpdate_hiddenLead_isEmpty() throws Exception {
		String leadId = scheduledLead();

		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				assertTrue(surveyLeads.findVisibleForUpdate(leadId, viewer(otherSale)).isEmpty()));
	}

	@Test
	void findVisibleForUpdate_outsideTransaction_isRefused() throws Exception {
		String leadId = scheduledLead();

		assertThrows(IllegalTransactionStateException.class,
				() -> surveyLeads.findVisibleForUpdate(leadId, viewer(sale)));
	}

	/** plan PD-5: the move reuses the row the command locked, in the same transaction. */
	@Test
	void applySurveyMove_afterTheLockedRead_reusesTheRow() throws Exception {
		String leadId = scheduledLead();
		Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);

		SurveyLeadMoveResult result = new TransactionTemplate(transactionManager).execute(tx -> {
			long version = surveyLeads.findVisibleForUpdate(leadId, viewer(sale)).orElseThrow().version();
			return surveyLeads.applySurveyMove(leadId, version, new SurveyLeadMove(SurveyLeadEvent.CHECKLIST_REVIEWED,
					SurveyLeadTarget.QUALIFIED, leader.userId(), CHECKLIST, 3, at, "PASSED", null));
		});

		assertEquals("QUALIFIED", result.status());
		assertEquals("QUALIFIED", survey.leadStatus(leadId));
		Map<String, Object> row = jdbc.queryForMap("select type, from_status, to_status, reference_id, reference_no,"
				+ " reference_detail, note from owner_lead_activities where lead_id = ? and type = 'CHECKLIST_REVIEWED'",
				leadId);
		assertEquals("SURVEY_SCHEDULED", row.get("from_status"));
		assertEquals("QUALIFIED", row.get("to_status"));
		assertEquals(CHECKLIST, row.get("reference_id"));
		assertEquals(3, row.get("reference_no"));
		assertEquals("PASSED", row.get("reference_detail"));
		assertEquals(null, row.get("note"));
	}

	/** spec 4.9: the longest detail fits the widened column and comes back as written. */
	@Test
	void applySurveyMove_longestDetail_roundTrips() throws Exception {
		String leadId = scheduledLead();
		Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);

		new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
			long version = surveyLeads.findVisibleForUpdate(leadId, viewer(sale)).orElseThrow().version();
			surveyLeads.applySurveyMove(leadId, version, new SurveyLeadMove(SurveyLeadEvent.CHECKLIST_REVIEWED,
					SurveyLeadTarget.CONTACTED, leader.userId(), CHECKLIST, 3, at, "NEEDS_CHANGES:RESURVEY_REQUIRED",
					null));
		});

		assertEquals("CONTACTED", survey.leadStatus(leadId));
		assertEquals("NEEDS_CHANGES:RESURVEY_REQUIRED", jdbc.queryForObject("select reference_detail from"
				+ " owner_lead_activities where lead_id = ? and type = 'CHECKLIST_REVIEWED'", String.class, leadId));
	}

	/** A lead of the sale with survey work; how it got there does not matter to a lock. */
	private String scheduledLead() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		jdbc.update("update owner_leads set status = 'SURVEY_SCHEDULED' where id = ?", leadId);
		return leadId;
	}

	private static boolean probe(Connection connection, String sql, String id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, id);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	/** The table-level lock modes the holder has on owner_leads, as pg_locks shows them. */
	private static List<String> rowLockModes(Connection connection, String leadId, int holder) throws SQLException {
		List<String> modes = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("select l.mode from pg_locks l"
				+ " join pg_class c on c.oid = l.relation where c.relname = 'owner_leads' and l.pid = ?")) {
			statement.setInt(1, holder);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					modes.add(rows.getString(1));
				}
			}
		}
		return modes;
	}
}
