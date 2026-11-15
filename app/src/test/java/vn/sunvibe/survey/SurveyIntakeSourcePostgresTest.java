package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingUseCase;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingView;
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
	private SourceStandingUseCase sourceStandings;

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
		assertEquals(List.of(new IntakeSourceView.MarkedRevision(first.checklistId(), "WRONG_SCOPE",
				requalifiedAt(first.checklistId()))),
				source.markedRevisions());
		assertEquals(source.markedRevisions(), marked(first.leadId()));
	}

	/** D5-12: the marks stay readable with no current PASSED, so readiness can name why a result ended. */
	@Test
	void source_marks_readableWithNoCurrentPassed() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "3 Nguyễn Văn Linh");

		fixture.requalify(leader, passed.checklistId(), "WRONG_ASSET");

		assertEquals(List.of(new IntakeSourceView.MarkedRevision(passed.checklistId(), "WRONG_ASSET",
				requalifiedAt(passed.checklistId()))),
				marked(passed.leadId()));
	}

	/** SV-215: each marked revision carries the time of its mark, the revision's requalified_at. */
	@Test
	void markedRevisions_carryTheMarkTime() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "8 Nguyễn Văn Linh");

		fixture.requalify(leader, passed.checklistId(), "PHOTO_PROBLEM");

		List<IntakeSourceView.MarkedRevision> marks = marked(passed.leadId());
		assertEquals(1, marks.size());
		assertNotNull(marks.getFirst().markedAt());
		assertEquals(requalifiedAt(passed.checklistId()), marks.getFirst().markedAt());
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

	// ==================== read 2 of E05-S6b: parity with currentSource and markedRevisions (SR-6) ====================

	@Test
	void source_currentPassed_allFields_standingsMatch() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", "12 Bạch Đằng", "P101", "P102");

		assertStandingsMatch(passed.leadId());
	}

	@Test
	void source_marked_eachRevisionWithKind_standingsMatch() throws Exception {
		Passed first = fixture.whole(leader, sale, "HOUSE", "2 Nguyễn Văn Linh");
		fixture.requalify(leader, first.checklistId(), "WRONG_SCOPE");
		assertStandingsMatch(first.leadId());

		fixture.revisit(leader, sale, first.leadId(), "WHOLE", "HOUSE", "2 Nguyễn Văn Linh", "A1");

		assertStandingsMatch(first.leadId());
	}

	@Test
	void source_marks_readableWithNoCurrentPassed_standingsMatch() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "3 Nguyễn Văn Linh");

		fixture.requalify(leader, passed.checklistId(), "WRONG_ASSET");

		assertStandingsMatch(passed.leadId());
	}

	@Test
	void markedRevisions_carryTheMarkTime_standingsMatch() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "8 Nguyễn Văn Linh");

		fixture.requalify(leader, passed.checklistId(), "PHOTO_PROBLEM");

		assertStandingsMatch(passed.leadId());
	}

	@Test
	void source_noPassed_empty_standingsMatch() throws Exception {
		String lead = fixture.survey().lead(leader, sale, "CONTACTED");

		assertStandingsMatch(lead);
	}

	@Test
	void source_evidenceHeld_notUsable_standingsMatch() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "6 Nguyễn Văn Linh");
		jdbc.update("update survey_media set held_at = now(), held_by_user_id = ?, hold_reason = 'SECURITY_INCIDENT' where id = ?",
				leader.userId(), passed.photoIds().getFirst());

		assertStandingsMatch(passed.leadId());
	}

	@Test
	void standings_evidenceGone_matchesCurrentSource() throws Exception {
		Passed passed = fixture.whole(leader, sale, "HOUSE", "9 Nguyễn Văn Linh");
		jdbc.update("update survey_media set content_missing_at = ? where id = ?", Timestamp.from(Instant.now()),
				passed.photoIds().getFirst());

		assertStandingsMatch(passed.leadId());
		assertEquals("EVIDENCE_GONE", standingOf(passed.leadId()).reasonOrNull());
	}

	@Test
	void standings_twoScenariosInOneCall_eachAsAlone() throws Exception {
		Passed marked = fixture.whole(leader, sale, "HOUSE", "10 Nguyễn Văn Linh");
		fixture.requalify(leader, marked.checklistId(), "WRONG_SCOPE");
		fixture.revisit(leader, sale, marked.leadId(), "WHOLE", "HOUSE", "10 Nguyễn Văn Linh", "A1");
		Passed held = fixture.whole(leader, sale, "HOUSE", "11 Nguyễn Văn Linh");
		jdbc.update("update survey_media set held_at = now(), held_by_user_id = ?, hold_reason = 'SECURITY_INCIDENT' where id = ?",
				leader.userId(), held.photoIds().getFirst());
		Map<String, Integer> cycles = new LinkedHashMap<>();
		cycles.put(marked.leadId(), cycleOf(marked.leadId()));
		cycles.put(held.leadId(), cycleOf(held.leadId()));

		Map<String, SourceStandingView> both = standings(cycles);

		assertEquals(cycles.keySet(), both.keySet());
		assertEquals(standingOf(marked.leadId()), both.get(marked.leadId()));
		assertEquals(standingOf(held.leadId()), both.get(held.leadId()));
	}

	@Test
	void standings_unknownLead_noSourceNoMarks() {
		String unknown = "01LEAD0000000000000000000A";

		assertEquals(Map.of(unknown, new SourceStandingView(null, false, null, List.of())), standings(Map.of(unknown, 1)));
	}

	@Test
	void standings_noTransaction_refused() {
		assertThrows(IllegalTransactionStateException.class,
				() -> sourceStandings.standings(Map.of("01LEAD0000000000000000000A", 1)));
	}

	/** Read 2 for the lead and its current cycle answers what currentSource and markedRevisions answer. */
	private void assertStandingsMatch(String leadId) {
		SourceStandingView standing = standingOf(leadId);
		Optional<IntakeSourceView> current = current(leadId);

		assertEquals(current.map(IntakeSourceView::revisionId).orElse(null), standing.revisionIdOrNull());
		assertEquals(current.map(IntakeSourceView::usable).orElse(false), standing.usable());
		assertEquals(current.map(IntakeSourceView::reasonOrNull).orElse(null), standing.reasonOrNull());
		assertEquals(marked(leadId), standing.markedRevisions());
	}

	private SourceStandingView standingOf(String leadId) {
		Map<String, SourceStandingView> answered = standings(Map.of(leadId, cycleOf(leadId)));
		assertEquals(Set.of(leadId), answered.keySet());
		return answered.get(leadId);
	}

	private Map<String, SourceStandingView> standings(Map<String, Integer> intentCycleByLead) {
		return new TransactionTemplate(transactionManager).execute(status -> sourceStandings.standings(intentCycleByLead));
	}

	private int cycleOf(String leadId) {
		return jdbc.queryForObject("select intent_cycle from owner_leads where id = ?", Integer.class, leadId);
	}

	private Optional<IntakeSourceView> current(String leadId) {
		return new TransactionTemplate(transactionManager).execute(status -> sources.currentSource(leadId,
				jdbc.queryForObject("select intent_cycle from owner_leads where id = ?", Integer.class, leadId)));
	}

	private List<IntakeSourceView.MarkedRevision> marked(String leadId) {
		return new TransactionTemplate(transactionManager).execute(status -> sources.markedRevisions(leadId));
	}

	private Instant requalifiedAt(String checklistId) {
		return jdbc.queryForObject("select requalified_at from survey_checklists where id = ?", Timestamp.class, checklistId)
				.toInstant();
	}
}
