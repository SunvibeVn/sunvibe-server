package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.core.application.usecase.checklist.IntakeSourceView;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyIntakeSourceUseCase;

/**
 * What survey-service publishes for the inventory intake (property-create intake spec 5.1), on real PASSED revisions: the
 * frozen source of the current unmarked PASSED, every marked revision of the lead with its kind, and nothing written on
 * read. Needs Docker.
 */
class SurveyIntakeSourcePostgresTest extends MediaApiTestBase {

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
	private MediaTestStore store;

	@Autowired
	private MediaTestSwitches switches;

	@Autowired
	private SurveyIntakeSourceUseCase sources;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private PassedRevisionFixture fixture;
	private TestUser sale;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		fixture = new PassedRevisionFixture(api, jdbc, ulidGenerator, store, "0961");
		sale = fixture.survey().user("Source Sale", "SALE");
		leader = fixture.survey().user("Source Leader", "SALE_LEADER");
	}

	@BeforeEach
	void open() {
		MediaIntakeFixture.open(jdbc);
		switches.submission(true);
	}

	@AfterEach
	void reset() {
		store.reset();
		switches.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		fixture.survey().cleanUp();
	}

	@Test
	void source_currentPassed_allFields() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", "12 Bạch Đằng", "P101", "P102");

		IntakeSourceView source = current(passed.leadId()).orElseThrow();

		assertEquals(passed.checklistId(), source.revisionId());
		assertNotNull(source.decidedAt());
		assertEquals(sale.userId(), source.performedBy());
		assertEquals(sale.userId(), source.recordedBy());
		assertEquals(jdbc.queryForObject("select content_sha256 from survey_checklist_submissions where checklist_id = ?",
				String.class, passed.checklistId()), source.contentSha256());
		assertEquals("PARTS", source.rentalScope());
		assertEquals("WHOLE_BUILDING", source.assetTypeOrNull());
		assertEquals("12 Bạch Đằng", source.addressText());
		assertEquals(SurveyApiTestSupport.WARD, source.wardCode());
		assertEquals("Hải Châu", source.wardName());
		assertEquals("Đà Nẵng", source.provinceName());
		assertNotNull(source.sourceDate());
		assertTrue(source.usable());
		assertEquals(2, source.spaces().size());
		IntakeSourceView.Space first = source.spaces().getFirst();
		assertEquals(passed.spaceIds().getFirst(), first.spaceId());
		assertEquals("P101", first.code());
		assertEquals("Floor 1", first.location());
		assertEquals("ENTERED_ALL", first.accessLimits());
		assertEquals(0, new BigDecimal("20").compareTo(first.areaSqm()));
		assertEquals(1, first.bedrooms());
		assertEquals(1, first.privateBathrooms());
		assertEquals("VACANT", first.condition());
		assertEquals(0, new BigDecimal("21").compareTo(source.spaces().get(1).areaSqm()));
		assertTrue(first.sources().stream().anyMatch(field -> "area".equals(field.field())
				&& "MEASURED".equals(field.source())));
		assertFalse(source.terms().isEmpty());
		assertTrue(source.markedRevisions().isEmpty());
	}

	/** G8 is reference only: an unknown term stays in the list with no value, so the intake can show what is open. */
	@Test
	void source_unknownTerms_listedWithoutValue() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "1 Nguyễn Văn Linh");

		IntakeSourceView source = current(passed.leadId()).orElseThrow();

		assertTrue(source.terms().stream().anyMatch(term -> "electricity".equals(term.name())
				&& term.amount() == null && term.text() == null && term.source() == null), source.terms().toString());
	}

	@Test
	void source_marked_eachRevisionWithKind() throws Exception {
		Passed first = fixture.whole(leader, sale, "HOUSE", "2 Nguyễn Văn Linh");
		fixture.requalify(leader, first.checklistId(), "WRONG_SCOPE");
		assertEquals(Optional.empty(), current(first.leadId()));

		Passed second = fixture.revisit(leader, sale, first.leadId(), "WHOLE", "HOUSE", "2 Nguyễn Văn Linh", "A1");

		IntakeSourceView source = current(first.leadId()).orElseThrow();
		assertEquals(second.checklistId(), source.revisionId());
		assertEquals(List.of(new IntakeSourceView.MarkedRevision(first.checklistId(), "WRONG_SCOPE")),
				source.markedRevisions());
		assertEquals(source.markedRevisions(), marked(first.leadId()));
	}

	/** D5-12: the marks stay readable with no current PASSED, so readiness can name why a result ended. */
	@Test
	void source_marks_readableWithNoCurrentPassed() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "3 Nguyễn Văn Linh");

		fixture.requalify(leader, passed.checklistId(), "WRONG_ASSET");

		assertEquals(List.of(new IntakeSourceView.MarkedRevision(passed.checklistId(), "WRONG_ASSET")),
				marked(passed.leadId()));
	}

	/** D5-20, example 3: P101's balcony reads on P101 and never on P102. */
	@Test
	void source_amenitiesPerSpace() throws Exception {
		Passed passed = fixture.partsWithAmenity(leader, sale, "WHOLE_BUILDING", "4 Nguyễn Văn Linh", "BALCONY", 0, "P101",
				"P102");

		IntakeSourceView source = current(passed.leadId()).orElseThrow();

		assertEquals(List.of("BALCONY"), source.spaces().get(0).amenities());
		assertEquals(List.of(), source.spaces().get(1).amenities());
	}

	@Test
	void source_noPassed_empty() throws Exception {
		String lead = fixture.survey().lead(leader, sale, "CONTACTED");

		assertEquals(Optional.empty(), current(lead));
		assertEquals(List.of(), marked(lead));
	}

	@Test
	void source_noTransaction_refused() {
		assertThrows(IllegalTransactionStateException.class, () -> sources.currentSource("01LEAD0000000000000000000A", 1));
		assertThrows(IllegalTransactionStateException.class, () -> sources.markedRevisions("01LEAD0000000000000000000A"));
	}

	/** spec 5.1: never ChecklistContextLoader, which writes security events through REQUIRES_NEW. */
	@Test
	void source_writesNoSecurityEvent() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "5 Nguyễn Văn Linh");
		int before = fixture.checklists().securityEvents(passed.leadId()).size();

		current(passed.leadId());

		assertEquals(before, fixture.checklists().securityEvents(passed.leadId()).size());
	}

	@Test
	void source_evidenceHeld_notUsable() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "6 Nguyễn Văn Linh");
		jdbc.update("update survey_media set held_at = now(), held_by_user_id = ?, hold_reason = 'SECURITY_INCIDENT' where id = ?",
				leader.userId(), passed.photoIds().getFirst());

		IntakeSourceView source = current(passed.leadId()).orElseThrow();

		assertFalse(source.usable());
		assertEquals("EVIDENCE_HELD", source.reasonOrNull());
	}

	@Test
	void source_toString_hidesTheAddress() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "7 Nguyễn Văn Linh");

		assertFalse(current(passed.leadId()).orElseThrow().toString().contains("Nguyễn"));
	}

	private Optional<IntakeSourceView> current(String leadId) {
		return new TransactionTemplate(transactionManager).execute(status -> sources.currentSource(leadId,
				jdbc.queryForObject("select intent_cycle from owner_leads where id = ?", Integer.class, leadId)));
	}

	private List<IntakeSourceView.MarkedRevision> marked(String leadId) {
		return new TransactionTemplate(transactionManager).execute(status -> sources.markedRevisions(leadId));
	}
}
