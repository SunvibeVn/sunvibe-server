package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.listing.core.application.query.RevisionCommandResult;
import vn.sunvibe.listing.core.application.usecase.ListingPublicationCommandUseCase;
import vn.sunvibe.listing.core.domain.model.listing.DisplayConditions;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.Listing;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.model.listing.ListingRevision;
import vn.sunvibe.listing.core.domain.model.listing.ListingState;
import vn.sunvibe.listing.core.domain.model.listing.PutLiveMoment;
import vn.sunvibe.listing.core.domain.model.listing.RevisionStatus;
import vn.sunvibe.listing.core.domain.model.listing.UnitAvailability;
import vn.sunvibe.listing.core.domain.policy.listing.FreshnessPolicy;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;

/**
 * The statements of a put live on PostgreSQL (listing-publish E06-S6 spec PL-1, PL-4, A-2; plan E06S6-P9): the one
 * UPDATE of the listing row, read as the {@code n_tup_upd} difference of {@code listings} in
 * {@code pg_stat_xact_user_tables} inside the same transaction (a {@code SELECT ... FOR UPDATE} moves no counter of it),
 * and the statements of the working set and of the approved revision's number, counted by {@link StatementCounter}
 * around the repository call alone on the test thread. Its imports and properties are those of
 * {@link ListingRevisionStatementsPostgresTest}, so the classes share one context. Each listing's revision 1 is approved
 * over HTTP; its fixture records no freshness confirmation. Needs Docker.
 */
@Import(StatementCounter.Config.class)
class ListingPutLiveStatementsPostgresTest extends ListingPublicationApiTestBase {

	@Autowired
	private ListingDomainRepository listings;

	@Autowired
	private StatementCounter counter;

	@Autowired
	private ListingPublicationCommandUseCase publications;

	@Override
	String phonePrefix() {
		return "0973";
	}

	/** PL-1: the guarded write of a put live that publishes the listing updates the listing row once. */
	@Test
	void update_putLive_listingRowOneUpdate() throws Exception {
		String listingId = approvedListing();
		Instant now = now();

		long updates = tx(() -> {
			Listing listing = listings.findForPutLive(listingId, 1).orElseThrow();
			listing.tryPutLive(1, holding(listing, 1, now), PutLiveMoment.SALE_COMMAND, sale.userId(), now,
					ids.generateId(), ids.generateId());
			long before = listingUpdates();
			counter.count(() -> listings.update(listing));
			return listingUpdates() - before;
		});

		assertEquals(1, updates);
		assertEquals("PUBLISHED", jdbc.queryForObject("select state from listings where id = ?", String.class,
				listingId));
	}

	/** E06S6-D5 step (6): the target, the live and the approved revisions with their fees: four statements. */
	@Test
	void findForPutLive_targetLiveApproved_fourStatements() throws Exception {
		String listingId = approvedListing();
		assertEquals(201, copy(sale, listingId, "1", dataVersion(listingId)).status());
		assertEquals(200, submit(sale, listingId, "2", dataVersion(listingId)).status());
		decided(reviewer, listingId, "2", approval(dataVersion(listingId)));
		jdbc.update("update listing_revisions set status = 'LIVE', went_live_at = ? where listing_id = ?"
				+ " and revision_no = 1", Timestamp.from(Instant.now()), listingId);
		AtomicReference<Optional<Listing>> loaded = new AtomicReference<>();

		long statements = tx(() -> counter.count(() -> loaded.set(listings.findForPutLive(listingId, 2))));

		assertEquals(4, statements);
		assertEquals(Map.of(1, "LIVE", 2, "APPROVED"), loaded.get().map(ListingPutLiveStatementsPostgresTest::statuses)
				.orElse(Map.of()));
	}

	/** E06S6-D7: the number of the approved revision waiting, one statement; none for a listing without one. */
	@Test
	void findApprovedRevisionNo_oneStatement() throws Exception {
		String approved = approvedListing();
		String pendingOnly = pending();
		AtomicReference<Optional<Integer>> found = new AtomicReference<>();
		AtomicReference<Optional<Integer>> none = new AtomicReference<>();

		long statements = tx(() -> counter.count(() -> found.set(listings.findApprovedRevisionNo(approved))));
		long noneStatements = tx(() -> counter.count(() -> none.set(listings.findApprovedRevisionNo(pendingOnly))));

		assertEquals(List.of(1L, 1L), List.of(statements, noneStatements));
		assertEquals(Optional.of(1), found.get());
		assertEquals(Optional.empty(), none.get());
	}

	/** PL-4: a valid record that puts live updates the listing row once, on the version its record row stores. */
	@Test
	void recordFreshnessWithPutLive_valid_listingRowOneUpdate() throws Exception {
		String listingId = approvedListing();
		Instant now = now();
		LocalDate today = FreshnessPolicy.today(now);

		long updates = tx(() -> {
			Listing listing = listings.findForPutLive(listingId, null).orElseThrow();
			long loaded = listing.rowVersion();
			listing.tryPutLive(1, holding(listing, 1, now), PutLiveMoment.VALID_CONFIRMATION, null, now,
					ids.generateId(), ids.generateId());
			FreshnessRecord record = new FreshnessRecord(ids.generateId(), listingId, loaded + 1, today, now,
					sale.userId(), FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, null);
			long before = listingUpdates();
			listings.recordFreshnessWithPutLive(listing, loaded, now, new FreshnessSummary(today, true), record,
					new ListingHistoryEntry(ids.generateId(), ListingHistoryAction.FRESHNESS_RECORDED, null,
							sale.userId(), now));
			return listingUpdates() - before;
		});

		assertEquals(1, updates);
	}

	/**
	 * PL-1, A-2: the Sale's put live in the test's read-write transaction, which the use case joins: one UPDATE of the
	 * listing row (the row lock {@code SELECT ... FOR UPDATE} moves no counter), and the statements of the whole command
	 * as measured on the first green run.
	 */
	@Test
	void putLive_statementCount_measured() throws Exception {
		String listingId = waitingApproval().listingId();
		long version = dataVersion(listingId);
		AtomicReference<RevisionCommandResult> answer = new AtomicReference<>();

		List<Long> measured = tx(() -> {
			long before = listingUpdates();
			long statements = counter.count(() -> answer.set(publications.putLive(putLiveCommand(listingId, 1, version),
					saleViewer())));
			long after = listingUpdates();
			return List.of(after - before, statements);
		});

		assertEquals(new RevisionCommandResult(listingId, 1, RevisionStatus.LIVE, version + 1), answer.get());
		assertEquals(1, measured.get(0));
		// A-2, measured on the first green run (2026-11-22): the row and the standings, the working set (four), the row
		// lock, E05's evaluation under its share lock and its terms, property's availability, the freshness summary, the
		// guarded write with the revision rows and the history row, and the event row
		assertEquals(89L, measured.get(1));
		assertEquals("PUBLISHED", state(listingId));
	}

	/** A listing of {@code sale} whose revision 1 was submitted and approved over HTTP; no freshness confirmation. */
	private String approvedListing() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", approval(dataVersion(listingId)));
		return listingId;
	}

	/** The listing's tuples updated by this transaction so far (E06S6-P9); read on the transaction's connection. */
	private long listingUpdates() {
		return jdbc.queryForObject("select n_tup_upd from pg_stat_xact_user_tables where relname = 'listings'"
				+ " and schemaname = current_schema()", Long.class);
	}

	/** Every display condition holding for the loaded revision: its own terms in effect, available, confirmed today. */
	private static DisplayConditions holding(Listing listing, int revisionNo, Instant now) {
		return new DisplayConditions(ListingState.UNPUBLISHED, false, true, List.of(),
				listing.revision(revisionNo).orElseThrow().termsOrNull(), UnitAvailability.AVAILABLE, false,
				new FreshnessSummary(FreshnessPolicy.today(now), true));
	}

	private static Map<Integer, String> statuses(Listing listing) {
		Map<Integer, String> answer = new TreeMap<>();
		for (ListingRevision revision : listing.revisions()) {
			answer.put(revision.revisionNo(), revision.status().name());
		}
		return answer;
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}
