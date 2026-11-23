package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessRecord;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessSummary;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryAction;
import vn.sunvibe.listing.core.domain.model.listing.ListingHistoryEntry;
import vn.sunvibe.listing.core.domain.repository.ListingDomainRepository;
import vn.sunvibe.listing.core.domain.repository.ListingRow;
import vn.sunvibe.listing.core.domain.repository.ReconfirmationCandidate;

/**
 * The statements of the freshness record, the freshness read and the list on PostgreSQL (listing-publish E06-S3 spec
 * FR-14, E06S3-D6, E06S3-D7; SV-332 (4); plan E06S3-P4): the repository calls of a record are five statements (the
 * listing row, its summary, the guarded update and two inserts), of the read three, and one chunk of the list one.
 * {@link StatementCounter} counts the calling thread only and records no SQL text, so each count is taken around the
 * repository calls alone, inside a transaction on the test thread; the scope check and E05's reads stay outside it. The
 * listing is created over HTTP. Needs Docker.
 */
@Import(StatementCounter.Config.class)
class ListingFreshnessStatementsPostgresTest extends ListingRevisionApiTestBase {

	@Autowired
	private ListingDomainRepository listings;

	@Autowired
	private StatementCounter counter;

	@Override
	String phonePrefix() {
		return "0960";
	}

	/** FR-14, SV-332 (4): the listing row, its summary, then the guarded update, the record and its history row: five. */
	@Test
	void record_fiveStatements_repositoryCalls() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

		long statements = tx(() -> counter.count(() -> {
			ListingRow row = listings.findRow(listingId).orElseThrow();
			FreshnessSummary previous = listings.findFreshnessSummary(listingId);
			FreshnessRecord record = new FreshnessRecord(ids.generateId(), listingId, row.rowVersion() + 1, today(), now,
					sale.userId(), FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, null);
			listings.recordFreshness(listingId, row.rowVersion(), now, previous.after(record), record,
					new ListingHistoryEntry(ids.generateId(), ListingHistoryAction.FRESHNESS_RECORDED, null, sale.userId(),
							now));
		}));

		assertEquals(5, statements);
		assertEquals(List.of(version + 1, version + 1), List.of(dataVersion(listingId), jdbc.queryForObject(
				"select listing_row_version from listing_freshness_records where listing_id = ?", Long.class, listingId)));
	}

	/** FR-14: the listing row, its summary and its latest record: three statements. */
	@Test
	void freshness_threeStatements_repositoryCalls() throws Exception {
		String listingId = listingInEffect().listingId();
		recorded(listingId);
		AtomicReference<Optional<FreshnessRecord>> latest = new AtomicReference<>();

		long statements = tx(() -> counter.count(() -> {
			listings.findRow(listingId).orElseThrow();
			listings.findFreshnessSummary(listingId);
			latest.set(listings.findLatestFreshnessRecord(listingId));
		}));

		assertEquals(3, statements);
		assertEquals(dataVersion(listingId), latest.get().orElseThrow().listingRowVersion());
	}

	/** FR-14, E06S3-D7: a chunk of the list is one statement, the first one and one after a cursor alike. */
	@Test
	void reconfirmations_oneStatementPerChunk() throws Exception {
		LocalDate due = today().minusDays(16);
		List<String> listingIds = new ArrayList<>();
		for (int listing = 0; listing < 2; listing++) {
			String listingId = listingInEffect().listingId();
			jdbc.update("update listings set freshness_confirmed_on = ?, freshness_latest_valid = true where id = ?",
					Date.valueOf(due), listingId);
			listingIds.add(listingId);
		}
		List<String> inIdOrder = listingIds.stream().sorted().toList();
		LocalDate onOrBefore = today().minusDays(10);
		AtomicReference<List<ReconfirmationCandidate>> first = new AtomicReference<>();
		AtomicReference<List<ReconfirmationCandidate>> next = new AtomicReference<>();

		List<Long> statements = tx(() -> List.of(
				counter.count(() -> first.set(listings.findReconfirmationCandidates(onOrBefore, null, 251))),
				counter.count(() -> next.set(listings.findReconfirmationCandidates(onOrBefore,
						candidateOf(first.get(), inIdOrder.getFirst()), 251)))));

		assertEquals(List.of(1L, 1L), statements);
		assertTrue(ids(next.get()).contains(inIdOrder.getLast()), next.get().toString());
	}

	/** A valid record by the assigned Sale over HTTP. */
	private void recorded(String listingId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dataVersion", dataVersion(listingId));
		body.put("channel", "CALL");
		body.put("outcome", "AVAILABLE");
		body.put("termsStillApply", true);
		body.put("unitUnchanged", true);
		body.put("note", null);
		Result answer = api.post(sale, LISTINGS + "/" + listingId + "/freshness-records", body);
		assertEquals(201, answer.status(), answer.rawBody());
	}

	/** The candidate of the listing among the chunk. */
	private static ReconfirmationCandidate candidateOf(List<ReconfirmationCandidate> chunk, String listingId) {
		return chunk.stream().filter(candidate -> candidate.listingId().equals(listingId)).findFirst().orElseThrow();
	}

	private static List<String> ids(List<ReconfirmationCandidate> chunk) {
		return chunk.stream().map(ReconfirmationCandidate::listingId).toList();
	}

	private <T> T tx(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}
}
