package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.listing.core.application.command.RevisionStartCommand;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;

/**
 * Concurrent revision commands on PostgreSQL (listing-publish E06-S2 RD-8's race, RD-18; listing BR-3; E06S2-D3,
 * E06S2-D19; plan E06S2-P12, E06S1-P6): a start that loaded before another start committed waits on the listing row and
 * answers CONCURRENT_MODIFICATION; two free starts give one revision; a command locks the listing row before it writes
 * any revision row. Interleavings are forced with the held locks of {@link AgreementStopApiTestBase}; a sleep proves no
 * order (testing.md). A Linux run is required before the issue closes (SV-068). Needs Docker.
 */
class ListingRevisionConcurrencyPostgresTest extends ListingRevisionApiTestBase {

	@Override
	String phonePrefix() {
		return "0913";
	}

	/**
	 * RD-8, E06S2-P12: the first start holds its transaction open; the later start on the same data version loads, waits
	 * on the listing row at its guarded update, and once the first commits answers 409 CONCURRENT_MODIFICATION; one new
	 * revision.
	 */
	@Test
	void start_heldFirstStart_laterWaitsThenConcurrentModification() throws Exception {
		String listingId = closedListing();
		long version = dataVersion(listingId);
		RevisionRows before = revisionRows(listingId);
		Future<Result> later;
		try (Hold hold = new Hold(() -> listingCommands.startRevision(new RevisionStartCommand(listingId, version),
				new ListingViewer(sale.userId(), Set.of("ROLE_SALE"))))) {
			later = executor.submit(() -> startRevision(sale, listingId, version));
			awaitWaiters(1);
			assertFalse(later.isDone());
		}

		Result answer = later.get(60, TimeUnit.SECONDS);

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals(before.revisions() + 1, revisionRows(listingId).revisions());
		assertEquals(List.of(2), jdbc.queryForList("select revision_no from listing_revisions where listing_id = ?"
				+ " and status = 'DRAFT'", Integer.class, listingId));
		assertEquals(version + 1, dataVersion(listingId));
	}

	/**
	 * RD-8, E06S2-P16: two starts on the same data version released together, their interleaving not forced: one 201,
	 * one 409 CONCURRENT_MODIFICATION or OPEN_REVISION_EXISTS, no 500, exactly one new revision.
	 */
	@Test
	void start_freeRace_oneCreatedOneConflictOneRevision() throws Exception {
		String listingId = closedListing();
		long version = dataVersion(listingId);
		RevisionRows before = revisionRows(listingId);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Result>> starts = new ArrayList<>();
		for (int side = 0; side < 2; side++) {
			starts.add(executor.submit(() -> {
				start.await(30, TimeUnit.SECONDS);
				return startRevision(sale, listingId, version);
			}));
		}

		start.countDown();
		List<Result> answers = new ArrayList<>();
		for (Future<Result> started : starts) {
			answers.add(started.get(60, TimeUnit.SECONDS));
		}

		assertEquals(List.of(201, 409), answers.stream().map(Result::status).sorted().toList(), answers.toString());
		Result refused = answers.stream().filter(answer -> answer.status() == 409).findFirst().orElseThrow();
		assertTrue(Set.of("CONCURRENT_MODIFICATION", "OPEN_REVISION_EXISTS")
				.contains(refused.body().path("code").asString()), refused.rawBody());
		assertEquals(before.revisions() + 1, revisionRows(listingId).revisions());
	}

	/**
	 * RD-18, E06S2-D19 (a): the revision table held in SHARE mode stops a content save at its revision write; the listing
	 * row is already locked by the save's guarded update, so a NOWAIT lock of it fails with 55P03; after the release the
	 * save answers 200.
	 */
	@Test
	void save_heldRevisionTable_listingRowLockedFirst() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		Future<Result> save;
		try (Hold hold = new Hold(() -> jdbc.execute("lock table listing_revisions in share mode"))) {
			save = executor.submit(() -> saveContent(sale, listingId, "1", completeContentBody(version)));
			awaitWaiters(1);
			assertFalse(save.isDone());

			DataAccessException locked = assertThrows(DataAccessException.class, () -> jdbc.queryForList(
					"select id from listings where id = ? for update nowait", listingId));

			SQLException cause = assertInstanceOf(SQLException.class, locked.getMostSpecificCause());
			assertEquals("55P03", cause.getSQLState());
		}

		Result answer = save.get(60, TimeUnit.SECONDS);

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(version + 1, dataVersion(listingId));
	}

	/** A listing whose revision 1 was submitted and withdrawn: no revision is open, so a start is possible. */
	private String closedListing() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		completeDraft(drafting);
		Result submitted = submit(sale, listingId, "1", dataVersion(listingId));
		assertEquals(200, submitted.status(), submitted.rawBody());
		Result withdrawn = withdraw(sale, listingId, "1", dataVersion(listingId));
		assertEquals(200, withdrawn.status(), withdrawn.rawBody());
		return listingId;
	}
}
