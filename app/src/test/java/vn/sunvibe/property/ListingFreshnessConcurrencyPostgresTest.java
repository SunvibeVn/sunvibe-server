package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.listing.core.application.command.FreshnessRecordCommand;
import vn.sunvibe.listing.core.application.command.RevisionFeaturesInput;
import vn.sunvibe.listing.core.application.command.RevisionSaveCommand;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;

/**
 * Concurrent freshness records on PostgreSQL (listing-publish US-6 AC-1; listing BR-3; E06-S3 spec U-20, E06S3-D3, FR-9;
 * plan E06S3-P14): a record shares the guarded row-version update of the revision commands, so a later record or a
 * later content save on the same data version waits on the listing row and answers CONCURRENT_MODIFICATION, in either
 * order, and the loser writes nothing. Interleavings are forced with the held transactions of
 * {@link AgreementStopApiTestBase}; a sleep proves no order (testing.md). A Linux run is required before the issue
 * closes (SV-068). Needs Docker.
 */
class ListingFreshnessConcurrencyPostgresTest extends ListingRevisionApiTestBase {

	@Override
	String phonePrefix() {
		return "0917";
	}

	/**
	 * FR-9: the first record holds its transaction open after its guarded update; the later record on the same data
	 * version waits on the listing row and, once the first commits, answers 409 CONCURRENT_MODIFICATION; one record row,
	 * the first's, and nothing of the loser.
	 */
	@Test
	void record_heldFirstRecord_laterWaitsThenConcurrentModification() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		RevisionRows before = revisionRows(listingId);
		Future<Result> later;
		try (Hold hold = new Hold(() -> listingCommands.recordFreshness(new FreshnessRecordCommand(listingId, version,
				FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, null), viewer()))) {
			later = executor.submit(() -> record(listingId, version));
			awaitWaiters(1);
			assertFalse(later.isDone());
		}

		Result answer = later.get(60, TimeUnit.SECONDS);

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals(List.of(version + 1 + " CALL"), records(listingId));
		assertEquals(new RevisionRows(before.revisions(), before.fees(), before.history() + 1, version + 1),
				revisionRows(listingId));
	}

	/**
	 * U-20, E06S3-D3: a content save holds its transaction open after its guarded update; a record on the same data
	 * version waits on the listing row and, once the save commits, answers 409 CONCURRENT_MODIFICATION; no record row.
	 */
	@Test
	void record_heldRevisionSave_recordWaitsThenConcurrentModification() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		Future<Result> later;
		try (Hold hold = new Hold(() -> listingCommands.saveContent(new RevisionSaveCommand(listingId, 1, version, TITLE,
				null, noFeatures()), viewer()))) {
			later = executor.submit(() -> record(listingId, version));
			awaitWaiters(1);
			assertFalse(later.isDone());
		}

		Result answer = later.get(60, TimeUnit.SECONDS);

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals(List.of(), records(listingId));
		assertEquals(List.of(TITLE, version + 1), List.of(title(listingId), dataVersion(listingId)));
		assertEquals(0, count("select count(*) from listing_history where listing_id = ? and action = 'FRESHNESS_RECORDED'",
				listingId));
	}

	/**
	 * U-20, E06S3-D3: a record holds its transaction open after its guarded update; a content save on the same data
	 * version waits on the listing row and, once the record commits, answers 409 CONCURRENT_MODIFICATION; the revision's
	 * content stays unsaved.
	 */
	@Test
	void save_heldRecord_saveWaitsThenConcurrentModification() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		Future<Result> later;
		try (Hold hold = new Hold(() -> listingCommands.recordFreshness(new FreshnessRecordCommand(listingId, version,
				FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, true, null), viewer()))) {
			later = executor.submit(() -> saveContent(sale, listingId, "1", completeContentBody(version)));
			awaitWaiters(1);
			assertFalse(later.isDone());
		}

		Result answer = later.get(60, TimeUnit.SECONDS);

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertNull(title(listingId));
		assertEquals(List.of(version + 1 + " CALL"), records(listingId));
		assertEquals(version + 1, dataVersion(listingId));
	}

	/** The assigned Sale as the use case sees it. */
	private ListingViewer viewer() {
		return new ListingViewer(sale.userId(), Set.of("ROLE_SALE"));
	}

	/** A record over HTTP by the assigned Sale on that data version: by message, still available, both answers yes. */
	private Result record(String listingId, long dataVersion) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dataVersion", dataVersion);
		body.put("channel", "MESSAGE");
		body.put("outcome", "AVAILABLE");
		body.put("termsStillApply", true);
		body.put("unitUnchanged", true);
		body.put("note", null);
		return api.post(sale, LISTINGS + "/" + listingId + "/freshness-records", body);
	}

	/** The listing's records as {@code <row version> <channel>}, in row-version order. */
	private List<String> records(String listingId) {
		return jdbc.query("select listing_row_version, channel from listing_freshness_records where listing_id = ?"
				+ " order by listing_row_version", (row, rowNum) -> row.getLong(1) + " " + row.getString(2), listingId);
	}

	/** The title of the listing's revision 1. */
	private String title(String listingId) {
		return jdbc.queryForObject("select title from listing_revisions where listing_id = ? and revision_no = 1",
				String.class, listingId);
	}

	/** No feature chosen, as a command part. */
	private static RevisionFeaturesInput noFeatures() {
		return new RevisionFeaturesInput(null, false, false, false, null, null, false, false, false, false, false, null,
				false);
	}
}
