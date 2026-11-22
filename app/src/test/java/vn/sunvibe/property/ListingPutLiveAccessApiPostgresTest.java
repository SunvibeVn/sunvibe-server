package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * Who may put live and read the publication (listing-publish section 2, US-4 AC-2, US-5 AC-1; listing BR-5, BR-16;
 * E06-S6 spec E06S6-D2; PL-5, PL-11, PL-12): the put live for the SALE or SALE_LEADER holding the basis lead now, 403 for
 * every other role and every id, the one 404 for anyone else in those roles and for an unknown or malformed id or number;
 * a reassignment moves the right at once; the publication read with the listing read's roles and scope; the live
 * difference with the review read's. Every refused call writes nothing. Needs Docker.
 */
class ListingPutLiveAccessApiPostgresTest extends ListingPublicationApiTestBase {

	@Override
	String phonePrefix() {
		return "0975";
	}

	/** PL-5: 401, 403 for every id, the one 404; then the reassigned leader puts live. */
	@Test
	void putLive_rolesAndScope_401And403And404() throws Exception {
		Drafting drafting = waitingApproval();
		String listingId = drafting.listingId();
		long version = dataVersion(listingId);
		Written before = written();

		Map<String, String> answers = new LinkedHashMap<>();
		answers.put("no token", codeOf(putLive(null, listingId, "1", version)));
		for (TestUser caller : List.of(manager, admin, reviewer, customer)) {
			answers.put(caller.userId() + " listing", codeOf(putLive(caller, listingId, "1", version)));
			answers.put(caller.userId() + " unknown", codeOf(putLive(caller, UNKNOWN, "1", version)));
		}
		answers.put("other sale", codeOf(putLive(otherSale, listingId, "1", version)));
		answers.put("leader", codeOf(putLive(leader, listingId, "1", version)));
		answers.put("unknown listing", codeOf(putLive(sale, UNKNOWN, "1", version)));
		answers.put("malformed listing", codeOf(putLive(sale, "01", "1", version)));
		for (String revisionNo : List.of("9", "01", "x")) {
			answers.put("revision " + revisionNo, codeOf(putLive(sale, listingId, revisionNo, version)));
		}

		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("no token", "401 UNAUTHORIZED");
		for (TestUser caller : List.of(manager, admin, reviewer, customer)) {
			expected.put(caller.userId() + " listing", "403 FORBIDDEN");
			expected.put(caller.userId() + " unknown", "403 FORBIDDEN");
		}
		for (String key : List.of("other sale", "leader", "unknown listing", "malformed listing", "revision 9",
				"revision 01", "revision x")) {
			expected.put(key, "404 NOT_FOUND");
		}
		assertEquals(expected, answers);
		assertEquals(before, written());

		fixture.checklists().reassign(leader, drafting.chain().leadId(), leader);
		Result answer = putLive(leader, listingId, "1", version);

		assertEquals(200, answer.status(), answer.rawBody());
		assertAll(
				() -> assertEquals("LIVE", answer.body().path("status").asString()),
				() -> assertEquals("LIVE", revisionStatus(listingId, 1)),
				() -> assertEquals("PUBLISHED", state(listingId)),
				() -> assertEquals(List.of(eventRow(listingId)), listingEventRows(listingId)));
	}

	/** PL-11: the listing read's roles and scope. */
	@Test
	void publication_rolesAndScope_401And403And404And200() throws Exception {
		String listingId = waitingApproval().listingId();
		Written before = written();

		Map<String, String> answers = new LinkedHashMap<>();
		answers.put("no token", codeOf(publication(null, listingId)));
		answers.put("customer", codeOf(publication(customer, listingId)));
		answers.put("other sale", codeOf(publication(otherSale, listingId)));
		answers.put("unknown", codeOf(publication(sale, UNKNOWN)));
		answers.put("malformed", codeOf(publication(sale, "01")));
		List<String> readers = new ArrayList<>();
		for (TestUser caller : List.of(sale, leader, manager, admin, reviewer)) {
			Result read = publication(caller, listingId);
			readers.add(read.status() + " " + read.body().path("listingId").asString());
		}

		assertEquals(Map.of("no token", "401 UNAUTHORIZED", "customer", "403 FORBIDDEN", "other sale", "404 NOT_FOUND",
				"unknown", "404 NOT_FOUND", "malformed", "404 NOT_FOUND"), answers);
		assertEquals(List.of("200 " + listingId, "200 " + listingId, "200 " + listingId, "200 " + listingId,
				"200 " + listingId), readers);
		assertEquals(before, written());
	}

	/** PL-12: the review read's roles and scope, a SALE refused even as the assignee. */
	@Test
	void liveDifference_rolesAndScope_401And403And404() throws Exception {
		String listingId = waitingApproval().listingId();
		Written before = written();

		Map<String, String> answers = new LinkedHashMap<>();
		answers.put("no token", codeOf(liveDifference(null, listingId, "1")));
		answers.put("assigned sale", codeOf(liveDifference(sale, listingId, "1")));
		answers.put("customer", codeOf(liveDifference(customer, listingId, "1")));
		answers.put("unknown listing", codeOf(liveDifference(reviewer, UNKNOWN, "1")));
		answers.put("malformed listing", codeOf(liveDifference(reviewer, "01", "1")));
		answers.put("unknown revision", codeOf(liveDifference(reviewer, listingId, "9")));
		answers.put("malformed revision", codeOf(liveDifference(reviewer, listingId, "x")));
		List<String> readers = new ArrayList<>();
		for (TestUser caller : List.of(leader, manager, admin, reviewer)) {
			Result read = liveDifference(caller, listingId, "1");
			readers.add(read.status() + " " + read.body().path("comparison").asString());
		}

		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("no token", "401 UNAUTHORIZED");
		expected.put("assigned sale", "403 FORBIDDEN");
		expected.put("customer", "403 FORBIDDEN");
		for (String key : List.of("unknown listing", "malformed listing", "unknown revision", "malformed revision")) {
			expected.put(key, "404 NOT_FOUND");
		}
		assertEquals(expected, answers);
		assertEquals(List.of("200 NO_LIVE_REVISION", "200 NO_LIVE_REVISION", "200 NO_LIVE_REVISION",
				"200 NO_LIVE_REVISION"), readers);
		assertEquals(before, written());
	}
}
