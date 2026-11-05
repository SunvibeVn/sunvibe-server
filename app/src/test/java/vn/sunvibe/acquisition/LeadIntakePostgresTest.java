package vn.sunvibe.acquisition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeActivity;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeEvent;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeFacts;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport;
import vn.sunvibe.survey.MediaApiTestBase;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * What acquisition publishes for the inventory intake (property-create intake spec 5.1) on real leads: the viewer rules, the
 * no-viewer facts of the signal and the owner commands, the phone lookup, the row lock and the History entry that moves
 * nothing. Needs Docker.
 */
class LeadIntakePostgresTest extends MediaApiTestBase {

	private static final Set<String> SALE_AUTHORITIES = Set.of("ROLE_SALE");
	private static final Set<String> MANAGER_AUTHORITIES = Set.of("ROLE_SALES_MANAGER");

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
	private LeadIntakeUseCase leads;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0962");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		sale = survey.user("Intake Sale", "SALE");
		otherSale = survey.user("Intake Other Sale", "SALE");
		leader = survey.user("Intake Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	@Test
	void findVisible_otherSale_empty() throws Exception {
		String lead = survey.lead(leader, sale, "CONTACTED");

		assertEquals(Optional.empty(), inTransaction(() -> leads.findVisible(lead, viewer(otherSale))));
		assertEquals(sale.userId(), inTransaction(() -> leads.findVisible(lead, viewer(sale))).orElseThrow()
				.assignedUserIdOrNull());
		assertTrue(inTransaction(() -> leads.findVisible(lead, manager())).isPresent());
	}

	@Test
	void findVisible_malformedId_empty() {
		assertEquals(Optional.empty(), inTransaction(() -> leads.findVisible("not a lead id", manager())));
	}

	@Test
	void findVisibleForUpdate_readsFacts() throws Exception {
		String lead = survey.lead(leader, sale, "CONTACTED");

		LeadIntakeFacts facts = inTransaction(() -> leads.findVisibleForUpdate(lead, viewer(sale))).orElseThrow();

		assertEquals("CONTACTED", facts.status());
		assertEquals("LONG_TERM_RENT", facts.intent());
		assertEquals(1, facts.intentCycle());
		assertEquals("Hải Châu", facts.wardName());
		assertFalse(facts.closed());
		assertTrue(facts.requestCode().startsWith("SVQ-") || !facts.requestCode().isBlank());
	}

	@Test
	void systemFacts_noTransaction_refused() {
		assertThrows(IllegalTransactionStateException.class, () -> leads.systemFacts(List.of("01LEAD0000000000000000000A")));
	}

	/** spec 4.10: a SALE's own view hides candidate leads, so the signal reads with no viewer. */
	@Test
	void systemFacts_otherSalesLead_returned() throws Exception {
		String lead = survey.lead(leader, otherSale, "CONTACTED");

		Map<String, LeadIntakeFacts> facts = inTransaction(() -> leads.systemFacts(List.of(lead, "not a lead id")));

		assertEquals(Set.of(lead), facts.keySet());
		assertEquals(otherSale.userId(), facts.get(lead).assignedUserIdOrNull());
	}

	/** spec 4.3: a manager who was ever the assignee has worked the lead. */
	@Test
	void systemFacts_earlierAssignees() throws Exception {
		String lead = survey.lead(leader, sale, "CONTACTED");
		checklists.reassign(leader, lead, otherSale);

		LeadIntakeFacts facts = inTransaction(() -> leads.systemFacts(List.of(lead))).get(lead);

		assertEquals(otherSale.userId(), facts.assignedUserIdOrNull());
		assertEquals(Set.of(sale.userId(), otherSale.userId()), facts.earlierAssignees());
	}

	@Test
	void otherLeadsOfPhone_excludesThisLead() throws Exception {
		String first = survey.lead(leader, sale, "CONTACTED");
		String second = survey.submitted(survey.phone(first));

		assertEquals(List.of(second), inTransaction(() -> leads.otherOpenLeadIdsOfPhone(first, manager())));
		assertEquals(List.of(first), inTransaction(() -> leads.otherOpenLeadIdsOfPhone(second, manager())));
	}

	/** US-3 AC-2: a Sale sees the warning only for leads they can see, so it leaks no other lead. */
	@Test
	void otherLeadsOfPhone_saleSeesOnlyVisible() throws Exception {
		String mine = survey.lead(leader, sale, "CONTACTED");
		survey.submitted(survey.phone(mine));

		assertEquals(List.of(), inTransaction(() -> leads.otherOpenLeadIdsOfPhone(mine, viewer(sale))));
	}

	/** spec 5.3: a move of the lead waits for an owner's command, which holds the row in share mode. */
	@Test
	void lockForShare_blocksHoldUntilCommit() throws Exception {
		String lead = survey.lead(leader, sale, "CONTACTED");
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> inTransaction(() -> {
			assertTrue(leads.lockForShare(lead).isPresent());
			locked.countDown();
			await(release);
			return null;
		}));
		assertTrue(locked.await(30, TimeUnit.SECONDS));

		assertThrows(DataAccessException.class, () -> jdbc.queryForList(
				"select id from owner_leads where id = ? for no key update nowait", lead));

		release.countDown();
		holder.get(30, TimeUnit.SECONDS);
		assertEquals(1, jdbc.queryForList("select id from owner_leads where id = ? for no key update nowait", lead).size());
	}

	@Test
	void lockForShare_unknownLead_empty() {
		assertEquals(Optional.empty(), inTransaction(() -> leads.lockForShare("01LEAD0000000000000000000A")));
	}

	/** spec 4.12: an owner's withdrawal names no actor, so no owner name appears in the History. */
	@Test
	void activity_nullActor_ok() throws Exception {
		String lead = survey.lead(leader, sale, "CONTACTED");
		long version = survey.version(lead);

		inTransaction(() -> {
			leads.recordIntakeActivity(lead, new LeadIntakeActivity(LeadIntakeEvent.INVENTORY_INTAKE_SUSPENDED, null,
					"01INTAKE000000000000000001", 1, now()));
			return null;
		});

		Map<String, Object> row = jdbc.queryForMap("select * from owner_lead_activities where lead_id = ?"
				+ " and type = 'INVENTORY_INTAKE_SUSPENDED'", lead);
		assertNull(row.get("actor_user_id"));
		assertEquals(version, survey.version(lead), "the lead row does not move (BR-16)");
	}

	@Test
	void activity_referenceBothSet() throws Exception {
		String lead = survey.lead(leader, sale, "CONTACTED");

		inTransaction(() -> {
			leads.recordIntakeActivity(lead, new LeadIntakeActivity(LeadIntakeEvent.INVENTORY_INTAKE_COMPLETED,
					sale.userId(), "01INTAKE000000000000000002", 3, now()));
			return null;
		});

		Map<String, Object> row = jdbc.queryForMap("select * from owner_lead_activities where lead_id = ?"
				+ " and type = 'INVENTORY_INTAKE_COMPLETED'", lead);
		assertEquals("01INTAKE000000000000000002", row.get("reference_id"));
		assertEquals(3, row.get("reference_no"));
		assertEquals(sale.userId(), row.get("actor_user_id"));
		assertNull(row.get("reference_detail"));
		assertNull(row.get("note"));
	}

	/** spec 4.12: a suspension recorded before any intake has both references null. */
	@Test
	void activity_noIntake_bothReferencesNull() throws Exception {
		String lead = survey.lead(leader, sale, "CONTACTED");

		inTransaction(() -> {
			leads.recordIntakeActivity(lead, new LeadIntakeActivity(LeadIntakeEvent.INVENTORY_INTAKE_SUSPENDED,
					leader.userId(), null, null, now()));
			return null;
		});

		Map<String, Object> row = jdbc.queryForMap("select * from owner_lead_activities where lead_id = ?"
				+ " and type = 'INVENTORY_INTAKE_SUSPENDED'", lead);
		assertNull(row.get("reference_id"));
		assertNull(row.get("reference_no"));
	}

	@Test
	void activity_noTransaction_refused() {
		assertThrows(IllegalTransactionStateException.class, () -> leads.recordIntakeActivity(
				"01LEAD0000000000000000000A", new LeadIntakeActivity(LeadIntakeEvent.INVENTORY_INTAKE_RESTORED, null, null,
						null, now())));
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	private SurveyLeadViewer viewer(TestUser user) {
		return leads.viewerFor(user.userId(), SALE_AUTHORITIES);
	}

	private SurveyLeadViewer manager() {
		return leads.viewerFor(leader.userId(), MANAGER_AUTHORITIES);
	}

	private <T> T inTransaction(java.util.function.Supplier<T> body) {
		return new TransactionTemplate(transactionManager).execute(status -> body.get());
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(30, TimeUnit.SECONDS));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
