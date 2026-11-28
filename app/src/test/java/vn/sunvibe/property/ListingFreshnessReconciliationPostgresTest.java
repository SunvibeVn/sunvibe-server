package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * listing-publish E06-S7 spec E06S7-D15, criterion PB-18 (register SV-334 (5)): the freshness summary of a listing,
 * {@code listings.freshness_confirmed_on} and {@code freshness_latest_valid}, which the public read uses at every read,
 * equals its latest records after each write path the record command has: an invalid first record, a valid record, a
 * valid record that puts an approved revision live, an invalid record after a valid one, two records the same day. Each
 * method runs the reconciliation query of E06S7-D15 limited to its own listing and expects no row; both comparisons stay
 * {@code is distinct from}, so a null on either side is a difference. A pin of plan E06S7-P7, in {@code T/property}
 * where PB-18 names {@code T/listing}, for the chain of {@link ListingPublicationApiTestBase} (plan E06S7-P9). Needs
 * Docker.
 */
class ListingFreshnessReconciliationPostgresTest extends ListingPublicationApiTestBase {

	/**
	 * The listing's id when its summary columns differ from its records: the latest record by row version, and the date
	 * of the latest valid record by row version.
	 */
	private static final String RECONCILIATION = "select l.id from listings l"
			+ " left join lateral (select r.valid from listing_freshness_records r where r.listing_id = l.id"
			+ " order by r.listing_row_version desc limit 1) latest on true"
			+ " left join lateral (select r.contact_date from listing_freshness_records r where r.listing_id = l.id"
			+ " and r.valid order by r.listing_row_version desc limit 1) confirmed on true"
			+ " where l.id = ? and (l.freshness_latest_valid is distinct from latest.valid"
			+ " or l.freshness_confirmed_on is distinct from confirmed.contact_date)";

	@Override
	String phonePrefix() {
		return "0982";
	}

	/** An invalid first record: the summary holds no date and a latest record that is not valid. */
	@Test
	void record_invalidFirst_summaryEqualsRecords() throws Exception {
		String listingId = listingInEffect().listingId();

		recorded(listingId, invalidRecord(dataVersion(listingId)));

		assertEquals(List.of(), mismatches(listingId));
	}

	/** A valid record: the summary holds its date and a valid latest record. */
	@Test
	void record_valid_summaryEqualsRecords() throws Exception {
		String listingId = listingInEffect().listingId();

		recorded(listingId, validRecord(dataVersion(listingId)));

		assertEquals(List.of(), mismatches(listingId));
	}

	/** A valid record while an Approved revision waits: it puts the revision live in the record's own write. */
	@Test
	void record_validPutsLive_summaryEqualsRecords() throws Exception {
		String listingId = waitingApproval().listingId();

		recorded(listingId, validRecord(dataVersion(listingId)));

		assertEquals("PUBLISHED", state(listingId));
		assertEquals(List.of(), mismatches(listingId));
	}

	/** An invalid record after a valid one: the date of the valid one kept, the latest record not valid. */
	@Test
	void record_invalidAfterValid_summaryEqualsRecords() throws Exception {
		String listingId = listingInEffect().listingId();
		recorded(listingId, validRecord(dataVersion(listingId)));

		recorded(listingId, invalidRecord(dataVersion(listingId)));

		assertEquals(List.of(), mismatches(listingId));
	}

	/** Two records the same day: the later one by row version is the latest. */
	@Test
	void record_twoSameDay_summaryEqualsRecords() throws Exception {
		String listingId = listingInEffect().listingId();
		recorded(listingId, invalidRecord(dataVersion(listingId)));

		recorded(listingId, validRecord(dataVersion(listingId)));

		assertEquals(List.of(), mismatches(listingId));
	}

	private void recorded(String listingId, Map<String, Object> body) throws Exception {
		Result answer = recordFreshness(sale, listingId, body);
		assertEquals(201, answer.status(), answer.rawBody());
	}

	private List<String> mismatches(String listingId) {
		return jdbc.queryForList(RECONCILIATION, String.class, listingId);
	}
}
