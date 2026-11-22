package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The assigned Sale's put live over HTTP on PostgreSQL (listing-publish US-5 AC-1, AC-3, AC-4, BR-11; E06-S6 spec 3.1,
 * 4.1, E06S6-D5, D19, D21; PL-1, PL-2, PL-5 to PL-7): every condition holding, the revision goes live, the listing
 * Published, one row of the listing event; a second put live replaces the first with its time; each failing condition
 * refuses with exactly its reason and writes nothing; the reasons in their order with E05's groups right after the
 * advertising right; the checks of E06S6-D5 in order. Fixtures of plan E06S6-P6. Needs Docker.
 */
class ListingPutLiveApiPostgresTest extends ListingPublicationApiTestBase {

	@Override
	String phonePrefix() {
		return "0974";
	}

	/** PL-1, PL-6, E06S6-D21: LIVE, the state, the time, the version, the history row and the one event row. */
	@Test
	void putLive_allHold_liveReplacedPublishedOneEvent() throws Exception {
		String listingId = waitingApproval().listingId();
		long version = dataVersion(listingId);
		Set<String> history = new HashSet<>(history(listingId));

		Result answer = putLive(sale, listingId, "1", version);

		assertEquals(200, answer.status(), answer.rawBody());
		history.add("REVISION_PUT_LIVE 1 " + sale.userId());
		assertAll(
				() -> assertEquals(List.of(listingId, "1", "LIVE", String.valueOf(version + 1)), List.of(
						answer.body().path("listingId").asString(), answer.body().path("revisionNo").asString(),
						answer.body().path("status").asString(), answer.body().path("dataVersion").asString())),
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals("LIVE true false", revisionTimes(listingId, 1)),
				() -> assertEquals(version + 1, dataVersion(listingId)),
				() -> assertEquals(history, new HashSet<>(history(listingId))),
				() -> assertEquals(List.of(eventRow(listingId)), listingEventRows(listingId)));
	}

	/** PL-6: a copy of the live revision approved while a condition failed, then put live: the former Replaced. */
	@Test
	void putLive_secondPutLive_formerReplacedWithItsTime() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		putLiveOk(listingId);
		assertEquals(201, copy(sale, listingId, "1", dataVersion(listingId)).status());
		assertEquals(200, submit(sale, listingId, "2", dataVersion(listingId)).status());
		unitAvailability(drafting.chain().unitId(), "OCCUPIED");
		decided(reviewer, listingId, "2", approval(dataVersion(listingId)));
		unitAvailability(drafting.chain().unitId(), "AVAILABLE");
		assertEquals("APPROVED", revisionStatus(listingId, 2));

		Result answer = putLive(sale, listingId, "2", dataVersion(listingId));

		assertEquals(200, answer.status(), answer.rawBody());
		assertAll(
				() -> assertEquals("REPLACED true true", revisionTimes(listingId, 1)),
				() -> assertEquals("LIVE true false", revisionTimes(listingId, 2)),
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals(List.of(eventRow(listingId), eventRow(listingId)), listingEventRows(listingId)));
	}

	/** PL-2: each condition failing alone, by the narrowest fixture: 409 with exactly its reason, nothing written. */
	@Test
	void putLive_eachCondition_refusedWithItsReasons() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		String agreementId = drafting.chain().agreementId();
		String unitId = drafting.chain().unitId();

		jdbc.update("update listings set state = 'PAUSED' where id = ?", listingId);
		assertRefused(listingId, List.of("reasons.LISTING_STATE"));
		jdbc.update("update listings set state = 'UNPUBLISHED' where id = ?", listingId);

		suspended(manager, agreementId);
		assertRefused(listingId, List.of("reasons.ADVERTISING_RIGHT", "reasonGroups.SUSPENDED"));
		Result released = releaseSuspension(admin, agreementId);
		assertEquals(204, released.status(), released.rawBody());

		jdbc.update("update listing_revisions set terms_monthly_rent = terms_monthly_rent + 1000000 where listing_id = ?"
				+ " and revision_no = 1", listingId);
		assertRefused(listingId, List.of("reasons.TERMS_NOT_IN_EFFECT"));
		jdbc.update("update listing_revisions set terms_monthly_rent = terms_monthly_rent - 1000000 where listing_id = ?"
				+ " and revision_no = 1", listingId);

		unitAvailability(unitId, "OCCUPIED");
		assertRefused(listingId, List.of("reasons.UNIT_NOT_AVAILABLE"));
		unitAvailability(unitId, "AVAILABLE");

		assertEquals(201, recordFreshness(sale, listingId, invalidRecord(dataVersion(listingId))).status());
		assertRefused(listingId, List.of("reasons.FRESHNESS"));
	}

	/** PL-2, E06S6-D19: every condition failing: the reasons in their order, E05's group right after its reason. */
	@Test
	void putLive_allConditionsFail_reasonsInOrderGroupsAfterAdvertising() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		assertEquals(201, recordFreshness(sale, listingId, invalidRecord(dataVersion(listingId))).status());
		jdbc.update("update listings set state = 'PAUSED' where id = ?", listingId);
		jdbc.update("update listing_revisions set terms_monthly_rent = terms_monthly_rent + 1000000 where listing_id = ?"
				+ " and revision_no = 1", listingId);
		unitAvailability(drafting.chain().unitId(), "OCCUPIED");
		suspended(manager, drafting.chain().agreementId());

		assertRefused(listingId, List.of("reasons.LISTING_STATE", "reasons.ADVERTISING_RIGHT", "reasonGroups.SUSPENDED",
				"reasons.TERMS_NOT_IN_EFFECT", "reasons.UNIT_NOT_AVAILABLE", "reasons.FRESHNESS"));
	}

	/**
	 * E06S6-D5: the checks in order, the first failing one answering, each with nothing written: the body before the
	 * role, the role, the scope, archived, the data version (a stale one with a revision number the listing does not
	 * have answers 409 before step (6) could answer 404), the revision Approved.
	 */
	@Test
	void putLive_checkOrder_badBodyForbiddenNotFoundArchivedStaleNotApproved() throws Exception {
		String listingId = waitingApproval().listingId();
		long version = dataVersion(listingId);
		String path = LISTINGS + "/" + listingId + "/revisions/1/publication";

		Written before = written();
		assertAll(
				() -> assertEquals("400 VALIDATION_FAILED [dataVersion]", codeAndFields(putLive(manager, listingId, "1",
						null))),
				() -> assertEquals("400 MALFORMED_REQUEST", codeOf(postRaw(manager, path, "{\"dataVersion\":"))),
				() -> assertEquals("403 FORBIDDEN", codeOf(putLive(manager, listingId, "1", version))),
				() -> assertEquals("404 NOT_FOUND", codeOf(putLive(otherSale, listingId, "1", version))));
		jdbc.update("update listings set state = 'ARCHIVED' where id = ?", listingId);
		assertEquals("409 LISTING_ARCHIVED", codeOf(putLive(sale, listingId, "1", version)));
		jdbc.update("update listings set state = 'UNPUBLISHED' where id = ?", listingId);
		assertEquals("409 CONCURRENT_MODIFICATION", codeOf(putLive(sale, listingId, "9", version - 1)));
		assertEquals(before, written());

		assertEquals(201, copy(sale, listingId, "1", version).status());
		Written copied = written();
		assertEquals("409 REVISION_NOT_APPROVED", codeOf(putLive(sale, listingId, "2", dataVersion(listingId))));
		assertEquals(copied, written());
		assertEquals("APPROVED", revisionStatus(listingId, 1));
		assertEquals(List.of(), listingEventRows(listingId));
	}

	/** The put live of revision 1 on the stored version: 409 PUT_LIVE_REFUSED with exactly these entries, nothing written. */
	private void assertRefused(String listingId, List<String> entries) throws Exception {
		Written before = written();

		Result answer = putLive(sale, listingId, "1", dataVersion(listingId));

		assertAll(
				() -> assertEquals("409 PUT_LIVE_REFUSED " + entries, codeAndFields(answer)),
				() -> assertEquals(before, written()),
				() -> assertEquals("APPROVED", revisionStatus(listingId, 1)),
				() -> assertEquals(List.of(), listingEventRows(listingId)));
	}
}
