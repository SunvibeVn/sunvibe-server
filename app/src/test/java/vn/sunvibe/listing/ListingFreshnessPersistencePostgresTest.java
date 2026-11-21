package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.listing.core.domain.exception.ListingChangedException;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionFeatures;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ReconfirmationCandidate;
import vn.sunvibe.shared.generator.id.IdGenerator;

/**
 * The freshness record's guarded write, the latest-record and summary reads and the reconfirmation candidates on a real
 * PostgreSQL (listing-publish E06-S3 spec 5.3, E06S3-D6, E06S3-D7; FR-3, FR-7, FR-9, FR-10). The annotations, autowired
 * fields, {@code RANDOM}, {@code created} with its cleanup and the helpers {@code listing}, {@code insert}, {@code tx},
 * {@code code} and {@code now} are copied from {@link ListingRevisionPersistencePostgresTest}, so this class shares its
 * context; the cleanup gains the delete of the freshness records first. Dates are 8, 11, 13 and 16 days before the
 * Vietnam date of the JVM clock, never on a boundary (E06S3-D14). Needs Docker.
 */
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
class ListingFreshnessPersistencePostgresTest {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String NOTE = "Chủ xác nhận còn trống.\nGọi lại sau 10 ngày.";

	@Autowired
	private ListingDomainRepository listings;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private IdGenerator<String> ids;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<String> created = new ArrayList<>();

	@AfterEach
	void deleteRows() {
		for (String id : created) {
			jdbc.update("DELETE FROM listing_freshness_records WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revision_fees WHERE revision_id IN"
					+ " (SELECT id FROM listing_revisions WHERE listing_id = ?)", id);
			jdbc.update("DELETE FROM listing_history WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listing_revisions WHERE listing_id = ?", id);
			jdbc.update("DELETE FROM listings WHERE id = ?", id);
		}
		created.clear();
	}

	@Test
	void recordFreshness_guardedUpdate_rowVersionColumnsRecordAndHistory() {
		Listing listing = stored();
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);
		String actor = ids.generateId();
		FreshnessRecord record = new FreshnessRecord(ids.generateId(), listing.id(), 1L, today, now, actor,
				FreshnessChannel.IN_PERSON, FreshnessOutcome.AVAILABLE, true, true, NOTE);
		ListingHistoryEntry entry = new ListingHistoryEntry(ids.generateId(), ListingHistoryAction.FRESHNESS_RECORDED,
				null, actor, now);

		tx(() -> {
			listings.recordFreshness(listing.id(), 0L, now, new FreshnessSummary(today, true), record, entry);
			return null;
		});

		assertEquals(1L, rowVersion(listing.id()));
		assertEquals(now, jdbc.queryForObject("SELECT updated_at FROM listings WHERE id = ?", Timestamp.class,
				listing.id()).toInstant());
		assertEquals(List.of(today, true), columns(listing.id()));
		assertEquals(List.of(record), storedRecords(listing.id()));
		assertEquals(List.of("FRESHNESS_RECORDED null " + actor + " " + now), history(listing.id()));
	}

	@Test
	void recordFreshness_staleVersion_listingChangedNothingWritten() {
		Listing listing = stored();
		jdbc.update("UPDATE listings SET row_version = row_version + 1 WHERE id = ?", listing.id());
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);
		FreshnessRecord record = new FreshnessRecord(ids.generateId(), listing.id(), 1L, today, now, ids.generateId(),
				FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, null);
		ListingHistoryEntry entry = new ListingHistoryEntry(ids.generateId(), ListingHistoryAction.FRESHNESS_RECORDED,
				null, record.recordedBy(), now);
		List<Object> before = written(listing.id());

		assertThrows(ListingChangedException.class, () -> tx(() -> {
			listings.recordFreshness(listing.id(), 0L, now, new FreshnessSummary(today, true), record, entry);
			return null;
		}));

		assertEquals(before, written(listing.id()));
	}

	@Test
	void findLatestFreshnessRecord_greatestRowVersionEvenWhenRecordedEarlier() {
		Listing listing = stored();
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);
		FreshnessRecord older = new FreshnessRecord(ids.generateId(), listing.id(), 1L, today, now, ids.generateId(),
				FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, null);
		FreshnessRecord latest = new FreshnessRecord(ids.generateId(), listing.id(), 2L, today.minusDays(1),
				now.minus(1, ChronoUnit.HOURS), ids.generateId(), FreshnessChannel.MESSAGE, FreshnessOutcome.UNREACHABLE,
				null, null, NOTE);
		insertRecord(older);
		insertRecord(latest);

		assertEquals(Optional.of(latest), tx(() -> listings.findLatestFreshnessRecord(listing.id())));
	}

	@Test
	void findFreshnessSummary_noRecord_none() {
		Listing listing = stored();

		assertEquals(FreshnessSummary.none(), tx(() -> listings.findFreshnessSummary(listing.id())));
	}

	@Test
	void findReconfirmationCandidates_dueOnly_orderedByDateThenId() {
		LocalDate today = FreshnessPolicy.today(now());
		String sixteen = insertListingRow("UNPUBLISHED", today.minusDays(16), true);
		String thirteenA = insertListingRow("PUBLISHED", today.minusDays(13), false);
		String thirteenB = insertListingRow("PAUSED", today.minusDays(13), true);
		String eleven = insertListingRow("URGENTLY_UNPUBLISHED", today.minusDays(11), true);
		String eight = insertListingRow("UNPUBLISHED", today.minusDays(8), true);
		String archived = insertListingRow("ARCHIVED", today.minusDays(16), true);
		String neverConfirmed = insertListingRow("UNPUBLISHED", null, null);
		List<String> due = Stream.of(sixteen, thirteenA, thirteenB, eleven).toList();

		List<String> returned = tx(() -> listings.findReconfirmationCandidates(
				FreshnessPolicy.dueOnOrBefore(now()), null, 251)).stream()
				.map(ReconfirmationCandidate::listingId)
				.toList();

		List<String> thirteens = Stream.of(thirteenA, thirteenB).sorted().toList();
		assertEquals(List.of(sixteen, thirteens.get(0), thirteens.get(1), eleven),
				returned.stream().filter(due::contains).toList());
		assertFalse(returned.contains(eight), "the 8-day listing");
		assertFalse(returned.contains(archived), "the stored archived listing");
		assertFalse(returned.contains(neverConfirmed), "the never confirmed listing");
	}

	@Test
	void findReconfirmationCandidates_keyset_nextChunkAfterCursor() {
		LocalDate today = FreshnessPolicy.today(now());
		String sixteen = insertListingRow("UNPUBLISHED", today.minusDays(16), true);
		String thirteen = insertListingRow("UNPUBLISHED", today.minusDays(13), false);
		String eleven = insertListingRow("UNPUBLISHED", today.minusDays(11), true);
		LocalDate onOrBefore = FreshnessPolicy.dueOnOrBefore(now());

		List<ReconfirmationCandidate> first = tx(() -> listings.findReconfirmationCandidates(onOrBefore, null, 2));

		assertEquals(List.of(sixteen, thirteen), first.stream().map(ReconfirmationCandidate::listingId).toList());
		List<ReconfirmationCandidate> next = tx(() -> listings.findReconfirmationCandidates(onOrBefore,
				first.get(1), 2));
		assertEquals(List.of(eleven), next.stream().map(ReconfirmationCandidate::listingId).toList());
	}

	@Test
	void recordFreshness_existingRevisionCommand_stillGuarded() {
		Listing listing = stored();
		Listing loaded = tx(() -> listings.findForCommand(listing.id(), 1)).orElseThrow();
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);
		FreshnessRecord record = new FreshnessRecord(ids.generateId(), listing.id(), 1L, today, now, ids.generateId(),
				FreshnessChannel.CALL, FreshnessOutcome.NOT_AVAILABLE, null, null, null);
		tx(() -> {
			listings.recordFreshness(listing.id(), 0L, now, new FreshnessSummary(null, false), record,
					new ListingHistoryEntry(ids.generateId(), ListingHistoryAction.FRESHNESS_RECORDED, null,
							record.recordedBy(), now));
			return null;
		});
		loaded.saveContent(1, new RevisionContent("Phòng trọ mới gần chợ", null, RevisionFeatures.NONE_CHOSEN),
				ids.generateId(), ids.generateId(), now());

		assertThrows(ListingChangedException.class, () -> tx(() -> {
			listings.update(loaded);
			return null;
		}));
	}

	/** The listing's row version, its two freshness columns, and its counts of records and history rows. */
	private List<Object> written(String listingId) {
		List<Object> state = new ArrayList<>();
		state.add(rowVersion(listingId));
		state.addAll(columns(listingId));
		state.add(count("SELECT count(*) FROM listing_freshness_records WHERE listing_id = ?", listingId));
		state.add(count("SELECT count(*) FROM listing_history WHERE listing_id = ?", listingId));
		return state;
	}

	/** The two freshness columns, the date as a LocalDate. */
	private List<Object> columns(String listingId) {
		Map<String, Object> row = jdbc.queryForMap("SELECT freshness_confirmed_on, freshness_latest_valid FROM listings"
				+ " WHERE id = ?", listingId);
		Object date = row.get("freshness_confirmed_on");
		return Arrays.asList(date == null ? null : ((Date) date).toLocalDate(), row.get("freshness_latest_valid"));
	}

	/** Every record row of the listing, read whole, by row version. */
	private List<FreshnessRecord> storedRecords(String listingId) {
		return jdbc.query("SELECT * FROM listing_freshness_records WHERE listing_id = ? ORDER BY listing_row_version",
				(rs, rowNum) -> new FreshnessRecord(rs.getString("id"), rs.getString("listing_id"),
						rs.getLong("listing_row_version"), rs.getDate("contact_date").toLocalDate(),
						rs.getTimestamp("recorded_at").toInstant(), rs.getString("recorded_by"),
						FreshnessChannel.valueOf(rs.getString("channel")), FreshnessOutcome.valueOf(rs.getString("outcome")),
						(Boolean) rs.getObject("terms_still_apply"), (Boolean) rs.getObject("unit_unchanged"),
						rs.getString("note")),
				listingId);
	}

	/** The listing's history rows other than CREATED: action, revision, actor and time. */
	private List<String> history(String listingId) {
		return jdbc.query("SELECT action, revision_no, actor_user_id, occurred_at FROM listing_history"
						+ " WHERE listing_id = ? AND action <> 'CREATED' ORDER BY occurred_at",
				(rs, rowNum) -> rs.getString("action") + " " + rs.getObject("revision_no") + " "
						+ rs.getString("actor_user_id") + " " + rs.getTimestamp("occurred_at").toInstant(),
				listingId);
	}

	private void insertRecord(FreshnessRecord record) {
		jdbc.update("INSERT INTO listing_freshness_records (id, listing_id, listing_row_version, contact_date,"
						+ " recorded_at, recorded_by, channel, outcome, terms_still_apply, unit_unchanged, valid, note)"
						+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", record.id(), record.listingId(),
				record.listingRowVersion(), Date.valueOf(record.contactDate()), Timestamp.from(record.recordedAt()),
				record.recordedBy(), record.channel().name(), record.outcome().name(), record.termsStillApplyOrNull(),
				record.unitUnchangedOrNull(), record.valid(), record.noteOrNull());
	}

	/** A listings row written with SQL only, on its own agreement, with the two freshness columns given. */
	private String insertListingRow(String state, LocalDate confirmedOnOrNull, Boolean latestValidOrNull) {
		String id = ids.generateId();
		created.add(id);
		Timestamp now = Timestamp.from(now());
		jdbc.update("INSERT INTO listings (id, code, unit_id, transaction_type, agreement_id, state, created_by,"
						+ " created_at, updated_at, freshness_confirmed_on, freshness_latest_valid)"
						+ " VALUES (?, ?, ?, 'LONG_TERM_RENT', ?, ?, ?, ?, ?, ?, ?)", id, code().value(), ids.generateId(),
				ids.generateId(), state, ids.generateId(), now, now,
				confirmedOnOrNull == null ? null : Date.valueOf(confirmedOnOrNull), latestValidOrNull);
		return id;
	}

	private long rowVersion(String listingId) {
		return jdbc.queryForObject("SELECT row_version FROM listings WHERE id = ?", Long.class, listingId);
	}

	private int count(String sql, Object... arguments) {
		return jdbc.queryForObject(sql, Integer.class, arguments);
	}

	/** A listing created and inserted as the create does: revision 1 DRAFT and its CREATED row. */
	private Listing stored() {
		Listing listing = listing(ids.generateId(), now());
		insert(listing);
		return listing;
	}

	private Listing listing(String agreementId, Instant now) {
		Listing listing = Listing.create(ids.generateId(), code(), ids.generateId(), agreementId, ids.generateId(), now,
				ids.generateId(), ids.generateId());
		created.add(listing.id());
		return listing;
	}

	private void insert(Listing listing) {
		tx(() -> {
			listings.insert(listing);
			return null;
		});
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private static ListingCode code() {
		return ListingCode.draw(RANDOM);
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
