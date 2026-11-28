package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The valid confirmation's try over HTTP on PostgreSQL (listing-publish US-5 AC-3 moment 2, US-6 AC-4; E06-S6 spec 3.3,
 * E06S6-D7; PL-4): a valid record while an Approved revision waits and every other condition holds puts it live in the
 * record's own write: the answer's data version is the record row's, the listing Published, one event row; a valid
 * record while a condition fails is stored as today and the revision waits. Needs Docker.
 */
class ListingPutLiveOnConfirmationApiPostgresTest extends ListingPublicationApiTestBase {

	@Override
	String phonePrefix() {
		return "0977";
	}

	/** PL-4: 201 with the data version sent + 1, the record row's; the revision Live; one event row. */
	@Test
	void record_validWithApproved_liveRecordVersionOneEvent() throws Exception {
		String listingId = waitingApproval().listingId();
		long version = dataVersion(listingId);

		Result answer = recordFreshness(sale, listingId, validRecord(version));

		assertEquals(201, answer.status(), answer.rawBody());
		long answered = answer.body().path("dataVersion").asLong();
		assertAll(
				() -> assertEquals(version + 1, answered),
				() -> assertEquals(version + 1, dataVersion(listingId)),
				() -> assertEquals(answered + " true", freshnessRecords(listingId).getLast()),
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals("LIVE true false", revisionTimes(listingId, 1)),
				() -> assertEquals(1, count("select count(*) from listing_history where listing_id = ? and action ="
						+ " 'REVISION_PUT_LIVE_ON_CONFIRMATION' and actor_user_id is null", listingId)),
				// listing-publish E06-S7 (S-12, E06S7-D12): the record's two rows; the put live's signal is the due-now one
				() -> assertEquals(List.of(eventRow(listingId), dueEventRow(listingId)), listingEventRows(listingId)));
	}

	/** PL-4: the unit OCCUPIED again: 201, the record stored, the revision Approved, Unpublished, no event row. */
	@Test
	void record_validWithReasons_recordStoredRevisionApproved() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		unitAvailability(drafting.chain().unitId(), "OCCUPIED");
		long version = dataVersion(listingId);
		int records = freshnessRecords(listingId).size();

		Result answer = recordFreshness(sale, listingId, validRecord(version));

		assertEquals(201, answer.status(), answer.rawBody());
		assertAll(
				() -> assertEquals(version + 1, answer.body().path("dataVersion").asLong()),
				() -> assertEquals(records + 1, freshnessRecords(listingId).size()),
				() -> assertEquals((version + 1) + " true", freshnessRecords(listingId).getLast()),
				() -> assertEquals("APPROVED", revisionStatus(listingId, 1)),
				() -> assertEquals("UNPUBLISHED", state(listingId)),
				// S-12: the record's two rows; the refused try appends none
				() -> assertEquals(List.of(eventRow(listingId), dueEventRow(listingId)), listingEventRows(listingId)));
	}
}
