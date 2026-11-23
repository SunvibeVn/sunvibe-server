package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.model.listing.ChangeReason;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingAccessLog;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionDecision;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.ReviewCheck;
import vn.sunvibe.listing.core.domain.model.listing.ReviewNote;
import vn.sunvibe.listing.core.domain.model.listing.UnitSnapshot;
import vn.sunvibe.listing.core.domain.repository.ListingAccessLogDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ReviewCandidate;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The review's persistence on a real PostgreSQL (listing-publish E06-S4 spec 5.3, E06S4-D3, D8, D10, D14, D18; plan
 * E06S4-P2, P4, P5; RV-8, RV-16): the authors of revisions through the copy chain in one statement, the review
 * candidates by keyset, the working set of a decision, the guarded write that cancels a waiting approval before it
 * approves and inserts the decision once, the copy with its fee rows and its source, the decision read and the access
 * log. The annotations are those of {@link ListingFreshnessPersistencePostgresTest}, so this class shares its context;
 * its rows are written through the repository or SQL under listings it created, children first, times from the JVM
 * clock. Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingReviewPersistencePostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();
	/** Before any time another test writes, so these candidates come first in the queue's order. */
	private static final Instant LONG_AGO = Instant.parse("2001-01-01T00:00:00Z");

	@Autowired
	private ListingDomainRepository listings;

	@Autowired
	private ListingAccessLogDomainRepository accessLogs;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<String> created = new ArrayList<>();
	private final List<Long> logs = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		for (String id : created) {
			jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id IN"
					+ " (SELECT id FROM listing_revisions WHERE listing_id = ?)", id);
			jdbc.update("DELETE FROM listing_history WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listings WHERE id = ?", id);
		}
		created.clear();
		for (Long id : logs) {
			jdbc.update("DELETE FROM listing_access_logs WHERE id = ?", id);
		}
		logs.clear();
	}

	/** E06S4-D3, plan E06S4-P4: creator, editors, submitter and the authors of the copy source; never the reviewer. */
	@Test
	void findAuthors_creatorEditorsSubmitterAndCopyChain() {
		String listing = insertListingRow("UNPUBLISHED");
		String other = insertListingRow("UNPUBLISHED");
		String a = ids.generateId();
		String b = ids.generateId();
		String c = ids.generateId();
		String d = ids.generateId();
		String e = ids.generateId();
		String f = ids.generateId();
		String g = ids.generateId();
		String reviewer = ids.generateId();
		String stranger = ids.generateId();
		String first = insertRevision(listing, 1, "NEEDS_CHANGES", a, null);
		history(listing, "REVISION_SAVED", 1, b);
		history(listing, "TERMS_CAPTURED", 1, c);
		history(listing, "REVISION_SUBMITTED", 1, d);
		history(listing, "REVISION_RETURNED", 1, reviewer);
		String second = insertRevision(listing, 2, "DRAFT", e, 1);
		history(listing, "REVISION_COPIED", 2, e);
		history(listing, "REVISION_SAVED", 2, f);
		history(listing, "REVISION_SUBMITTED", 2, g);
		history(listing, "REVISION_WITHDRAWN", 2, reviewer);
		insertRevision(other, 1, "DRAFT", stranger, null);
		history(other, "REVISION_SAVED", 1, stranger);

		Map<String, Set<String>> authors = tx(() -> listings.findAuthors(List.of(first, second)));

		assertEquals(Set.of(a, b, c, d), authors.get(first));
		assertEquals(Set.of(a, b, c, d, e, f, g), authors.get(second));
		assertFalse(authors.get(second).contains(reviewer));
		assertFalse(authors.get(first).contains(stranger));
		assertEquals(Set.of(a, b, c, d, e, f, g), tx(() -> listings.findAuthors(List.of(second))).get(second));
	}

	/** An empty set answers an empty map and sends no statement: the transaction is aborted, so any would fail. */
	@Test
	void findAuthors_emptySet_emptyNoStatement() {
		Map<String, Set<String>> authors = new TransactionTemplate(transactionManager).execute(status -> {
			assertThrows(DataAccessException.class, () -> jdbc.queryForObject("SELECT 1 / 0", Integer.class));
			Map<String, Set<String>> answer = listings.findAuthors(List.of());
			status.setRollbackOnly();
			return answer;
		});

		assertEquals(Map.of(), authors);
	}

	/** E06S4-D10, plan E06S4-P5: Pending review only, listings not stored archived, (submitted_at, id) keyset. */
	@Test
	void findReviewCandidates_pendingOnlyNotArchivedKeysetOrder() {
		String submittedSecond = insertListingRow("UNPUBLISHED");
		String submittedFirst = insertListingRow("PUBLISHED");
		String tiedA = insertListingRow("PAUSED");
		String tiedB = insertListingRow("UNPUBLISHED");
		String archived = insertListingRow("ARCHIVED");
		String closed = insertListingRow("UNPUBLISHED");
		String second = insertPending(submittedSecond, 1, LONG_AGO.plusSeconds(20));
		String firstId = insertPending(submittedFirst, 1, LONG_AGO.plusSeconds(10));
		String tieOne = insertPending(tiedA, 1, LONG_AGO.plusSeconds(30));
		String tieTwo = insertPending(tiedB, 1, LONG_AGO.plusSeconds(30));
		insertPending(archived, 1, LONG_AGO.plusSeconds(5));
		insertRevision(closed, 1, "CANCELLED", ids.generateId(), null);
		insertRevision(closed, 2, "NEEDS_CHANGES", ids.generateId(), null);
		insertRevision(closed, 3, "DRAFT", ids.generateId(), null);
		List<String> mine = List.of(second, firstId, tieOne, tieTwo);

		List<ReviewCandidate> chunk = tx(() -> listings.findReviewCandidates(null, null, 2));

		assertEquals(List.of(firstId, second), chunk.stream().map(ReviewCandidate::revisionId).toList());
		ReviewCandidate head = chunk.getFirst();
		assertAll(
				() -> assertEquals(submittedFirst, head.listingId()),
				() -> assertEquals(1, head.revisionNo()),
				() -> assertEquals(LONG_AGO.plusSeconds(10), head.submittedAt()),
				() -> assertEquals("Phòng trọ mới gần chợ Hải Châu", head.title()),
				() -> assertEquals(new UnitSnapshot("APARTMENT", "48", "Đà Nẵng", "20194", "Phường Hải Châu", 2, 1,
						32.5), head.unitOrNull()),
				() -> assertEquals(jdbc.queryForObject("SELECT code FROM listings WHERE id = ?", String.class,
						submittedFirst), head.code()),
				() -> assertEquals(jdbc.queryForObject("SELECT agreement_id FROM listings WHERE id = ?", String.class,
						submittedFirst), head.agreementId()));
		ReviewCandidate cursor = chunk.get(1);
		List<String> next = tx(() -> listings.findReviewCandidates(cursor.submittedAt(), cursor.revisionId(), 50))
				.stream().map(ReviewCandidate::revisionId).filter(mine::contains).toList();
		assertEquals(List.of(tieOne, tieTwo).stream().sorted().toList(), next);
	}

	/** Plan E06S4-P2: the target and the approved revision still waiting, each in its full form with its fees. */
	@Test
	void findForReview_targetAndWaitingApproval_fullForm() {
		String listing = insertListingRow("UNPUBLISHED");
		insertRevision(listing, 1, "APPROVED", ids.generateId(), null);
		insertRevision(listing, 2, "CANCELLED", ids.generateId(), null);
		String target = insertPending(listing, 3, now().minusSeconds(60));
		insertFee(target, 1, "Điện", "AMOUNT", 3_500L, "PER_KWH");

		Listing loaded = tx(() -> listings.findForReview(listing, 3)).orElseThrow();

		assertEquals(List.of(1, 3), loaded.revisions().stream().map(ListingRevision::revisionNo).toList());
		assertEquals(3, loaded.newestRevisionNo());
		assertTrue(loaded.revisions().stream().allMatch(ListingRevision::fullForm));
		assertEquals(RevisionStatus.APPROVED, loaded.revision(1).orElseThrow().status());
		assertEquals(1, loaded.revision(3).orElseThrow().termsOrNull().fees().size());
		assertEquals(List.of(1), tx(() -> listings.findForReview(listing, 9)).orElseThrow().revisions().stream()
				.map(ListingRevision::revisionNo).toList());
		assertTrue(tx(() -> listings.findForReview(ids.generateId(), 1)).isEmpty());
	}

	/** RV-8, E06S4-D8, D9: one transaction cancels the waiting approval, approves, and stores the decision once. */
	@Test
	void update_approvalCancelsWaitingFirst_bothWrittenWithDecision() {
		String listing = insertListingRow("UNPUBLISHED");
		insertRevision(listing, 1, "APPROVED", ids.generateId(), null);
		String target = insertPending(listing, 2, now().minusSeconds(60));
		String reviewer = ids.generateId();
		// two ids that differ in their last character only, so the history rows read by id come back in that order
		String base = ids.generateId().substring(0, 25);
		String cancelHistory = base + "0";
		String approveHistory = base + "1";
		Instant decidedAt = now();

		tx(() -> {
			Listing loaded = listings.findForReview(listing, 2).orElseThrow();
			loaded.decide(2, RevisionDecision.approved(target, 2, ReviewCheck.VERSION_1, reviewer, decidedAt),
					approveHistory, cancelHistory, decidedAt);
			listings.update(loaded);
			return null;
		});

		assertEquals(List.of("CANCELLED", "APPROVED"), jdbc.queryForList("SELECT status FROM listing_revisions"
				+ " WHERE listing_id = ? ORDER BY revision_no", String.class, listing));
		Map<String, Object> decision = jdbc.queryForMap("SELECT * FROM listing_revision_reviews WHERE revision_id = ?",
				target);
		assertAll(
				() -> assertEquals(listing, decision.get("listing_id")),
				() -> assertEquals("APPROVED", decision.get("outcome")),
				() -> assertEquals(1, ((Number) decision.get("checklist_version")).intValue()),
				() -> assertNull(decision.get("change_reasons")),
				() -> assertNull(decision.get("rejection_group")),
				() -> assertNull(decision.get("note")),
				() -> assertEquals(reviewer, decision.get("decided_by")),
				() -> assertEquals(decidedAt, ((Timestamp) decision.get("decided_at")).toInstant()));
		assertEquals(List.of("APPROVAL_CANCELLED 1 " + reviewer, "REVISION_APPROVED 2 " + reviewer),
				jdbc.queryForList("SELECT action || ' ' || revision_no || ' ' || actor_user_id FROM listing_history"
						+ " WHERE listing_id = ? ORDER BY id", String.class, listing));
		assertEquals(1L, rowVersion(listing));
		assertEquals("UNPUBLISHED", jdbc.queryForObject("SELECT state FROM listings WHERE id = ?", String.class, listing));
	}

	/** Spec 3.3's backstop: a decision row written without moving the version is answered as a concurrent change. */
	@Test
	void update_decisionRowAlreadyThere_listingChanged() {
		String listing = insertListingRow("UNPUBLISHED");
		String target = insertPending(listing, 1, now().minusSeconds(60));
		jdbc.update("INSERT INTO listing_revision_reviews (revision_id, listing_id, outcome, change_reasons, note,"
				+ " decided_by, decided_at) VALUES (?, ?, 'NEEDS_CHANGES', 'OTHER', 'Ghi chú', ?, ?)", target, listing,
				ids.generateId(), Timestamp.from(now()));
		RevisionDecision decision = RevisionDecision.rejected(target, 1,
				vn.sunvibe.listing.core.domain.model.listing.RejectionGroup.OTHER, ReviewNote.ofOrNull("Ghi chú khác"),
				ids.generateId(), now());

		assertThrows(ListingChangedException.class, () -> tx(() -> {
			Listing loaded = listings.findForReview(listing, 1).orElseThrow();
			loaded.decide(1, decision, ids.generateId(), ids.generateId(), now());
			listings.update(loaded);
			return null;
		}));

		assertEquals("PENDING_REVIEW", jdbc.queryForObject("SELECT status FROM listing_revisions WHERE id = ?",
				String.class, target));
		assertEquals("NEEDS_CHANGES", jdbc.queryForObject("SELECT outcome FROM listing_revision_reviews"
				+ " WHERE revision_id = ?", String.class, target));
		assertEquals(0, count("SELECT count(*) FROM listing_history WHERE listing_id = ?", listing));
		assertEquals(0L, rowVersion(listing));
	}

	/** E06S4-D14, D18: the copy's fee rows are the source's; a later save of the copy keeps its source. */
	@Test
	void update_copiedRevision_feesWrittenSourceKeptAfterSave() {
		String listing = insertListingRow("UNPUBLISHED");
		String source = insertPending(listing, 1, now().minusSeconds(60));
		insertFee(source, 1, "Điện", "AMOUNT", 3_500L, "PER_KWH");
		insertFee(source, 2, "Internet", "INCLUDED", null, null);
		jdbc.update("UPDATE listing_revisions SET status = 'NEEDS_CHANGES' WHERE id = ?", source);
		String copyId = ids.generateId();

		tx(() -> {
			Listing loaded = listings.findForCommand(listing, 1).orElseThrow();
			loaded.copyRevision(1, copyId, ids.generateId(), ids.generateId(), now());
			listings.update(loaded);
			return null;
		});

		assertEquals(fees(source), fees(copyId));
		assertEquals(2, fees(copyId).size());
		assertEquals(1, count("SELECT copied_from_revision_no FROM listing_revisions WHERE id = ?", copyId));
		tx(() -> {
			Listing loaded = listings.findForCommand(listing, 2).orElseThrow();
			loaded.saveContent(2, new RevisionContent("Tiêu đề sau khi sửa", null, RevisionFeatures.NONE_CHOSEN),
					ids.generateId(), ids.generateId(), now());
			listings.update(loaded);
			return null;
		});
		assertEquals(1, count("SELECT copied_from_revision_no FROM listing_revisions WHERE id = ?", copyId));
		assertEquals(2, tx(() -> listings.findRevision(listing, 2)).orElseThrow().termsOrNull().fees().size());
		assertEquals(1, tx(() -> listings.findRevision(listing, 2)).orElseThrow().copiedFromRevisionNoOrNull());
	}

	/** E06S4-D15: the stored decision read back with every field; an undecided revision answers none. */
	@Test
	void findDecision_written_readBackEveryField() {
		String listing = insertListingRow("UNPUBLISHED");
		String target = insertPending(listing, 1, now().minusSeconds(60));
		RevisionDecision decision = RevisionDecision.needsChanges(target, 1,
				List.of(ChangeReason.OTHER, ChangeReason.CONTENT_MISMATCH, ChangeReason.TERMS_MISMATCH),
				ReviewNote.ofOrNull("Mô tả thiếu diện tích.\nSửa phí điện."), ids.generateId(), now());
		assertEquals(Optional.empty(), tx(() -> listings.findDecision(listing, 1)));

		tx(() -> {
			Listing loaded = listings.findForReview(listing, 1).orElseThrow();
			loaded.decide(1, decision, ids.generateId(), ids.generateId(), decision.decidedAt());
			listings.update(loaded);
			return null;
		});

		assertEquals(Optional.of(decision), tx(() -> listings.findDecision(listing, 1)));
		assertEquals("CONTENT_MISMATCH,TERMS_MISMATCH,OTHER", jdbc.queryForObject("SELECT change_reasons FROM"
				+ " listing_revision_reviews WHERE revision_id = ?", String.class, target));
		assertEquals(Optional.empty(), tx(() -> listings.findDecision(listing, 2)));
		assertEquals(Optional.empty(), tx(() -> listings.findDecision(ids.generateId(), 1)));
	}

	/** E06S4-D12, D18: one row with the Snowflake id passed, user, listing, revision, action and time; no location. */
	@Test
	void record_accessLog_snowflakeIdNoLocationColumn() {
		long id = Math.abs(RANDOM.nextLong() % 1_000_000_000_000L) + 1;
		logs.add(id);
		String user = ids.generateId();
		String listing = ids.generateId();
		Instant at = now();

		tx(() -> {
			accessLogs.record(ListingAccessLog.unitLocationOpened(id, user, listing, 3, at));
			return null;
		});

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM listing_access_logs WHERE id = ?", id);
		assertEquals(Set.of("id", "user_id", "listing_id", "revision_no", "action", "accessed_at"), row.keySet());
		assertAll(
				() -> assertEquals(id, ((Number) row.get("id")).longValue()),
				() -> assertEquals(user, row.get("user_id")),
				() -> assertEquals(listing, row.get("listing_id")),
				() -> assertEquals(3, ((Number) row.get("revision_no")).intValue()),
				() -> assertEquals("UNIT_LOCATION_OPENED", row.get("action")),
				() -> assertEquals(at, ((Timestamp) row.get("accessed_at")).toInstant()));
	}

	/** A Pending review revision with every column it needs, submitted at the time given. */
	private String insertPending(String listingId, int number, Instant submittedAt) {
		String id = ids.generateId();
		Timestamp at = Timestamp.from(submittedAt);
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at, title, description, terms_source, terms_captured_at, terms_monthly_rent,"
				+ " terms_deposit_basis, terms_deposit_months, terms_minimum_term_months, terms_payment_period_months,"
				+ " unit_asset_type, unit_province_code, unit_province_name, unit_ward_code, unit_ward_name, unit_bedrooms,"
				+ " unit_bathrooms, unit_area_sqm, submitted_at, submitted_by) VALUES (?, ?, ?, 'PENDING_REVIEW', ?, ?, ?,"
				+ " 'Phòng trọ mới gần chợ Hải Châu', ?, 'IN_EFFECT', ?, 8000000, 'MONTHS', 2, 6, 1, 'APARTMENT', '48',"
				+ " 'Đà Nẵng', '20194', 'Phường Hải Châu', 2, 1, 32.5, ?, ?)", id, listingId, number, ids.generateId(), at,
				at, "Mô tả ".repeat(30), at, at, ids.generateId());
		return id;
	}

	/** A revision row with the header columns only, created by the user given, copied from the number given. */
	private String insertRevision(String listingId, int number, String status, String createdBy, Integer copiedFrom) {
		String id = ids.generateId();
		Timestamp at = Timestamp.from(now());
		jdbc.update("INSERT INTO listing_revisions (id, listing_id, revision_no, status, created_by, created_at,"
				+ " updated_at, copied_from_revision_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, listingId, number, status,
				createdBy, at, at, copiedFrom);
		return id;
	}

	private void history(String listingId, String action, int revisionNo, String actor) {
		jdbc.update("INSERT INTO listing_history (id, listing_id, action, revision_no, actor_user_id, occurred_at)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", ids.generateId(), listingId, action, revisionNo, actor,
				Timestamp.from(now()));
	}

	private void insertFee(String revisionId, int number, String name, String basis, Long amount, String chargeUnit) {
		jdbc.update("INSERT INTO listing_revision_fees (revision_id, fee_no, name, basis, amount, charge_unit)"
				+ " VALUES (?, ?, ?, ?, ?, ?)", revisionId, number, name, basis, amount, chargeUnit);
	}

	/** A revision's fee rows as text, without the revision id, in fee number order. */
	private List<String> fees(String revisionId) {
		return jdbc.queryForList("SELECT fee_no || ' ' || name || ' ' || basis || ' ' || coalesce(amount::text, '-')"
				+ " || ' ' || coalesce(charge_unit, '-') FROM listing_revision_fees WHERE revision_id = ? ORDER BY fee_no",
				String.class, revisionId);
	}

	/** A listings row written with SQL only, on its own agreement, in the stored state given. */
	private String insertListingRow(String state) {
		String id = ids.generateId();
		created.add(id);
		Timestamp at = Timestamp.from(now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at) VALUES (?, ?, ?, 'LONG_TERM_RENT', ?, ?, ?, ?, ?)",
				id, ListingCode.draw(RANDOM).value(), ids.generateId(), ids.generateId(), state, ids.generateId(), at, at);
		return id;
	}

	private long rowVersion(String listingId) {
		return jdbc.queryForObject("SELECT row_version FROM listings WHERE id = ?", Long.class, listingId);
	}

	private int count(String sql, Object... arguments) {
		return jdbc.queryForObject(sql, Integer.class, arguments);
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
