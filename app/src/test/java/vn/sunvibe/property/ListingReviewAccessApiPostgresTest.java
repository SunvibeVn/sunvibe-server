package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * Who reaches the review operations over HTTP on PostgreSQL (listing-publish US-4, section 2; listing BR-5; E06-S4
 * spec section 4, E06S4-D2; RV-19): no token 401 on every operation, a broken body included; the queue and the review
 * read 403 to a SALE and a CUSTOMER; the decision and the location 403 to the managers and a SALE; the decision read 403
 * to a CUSTOMER; the copy 403 to a reviewer, an ADMIN and a manager; malformed and unknown ids the one 404. A refused
 * call writes nothing, no access log row included. Needs Docker.
 */
class ListingReviewAccessApiPostgresTest extends ListingReviewApiTestBase {

	@Override
	String phonePrefix() {
		return "0902";
	}

	/** RV-19: no token, 401 UNAUTHORIZED on the six operations, the decision and the copy with a valid and a broken body. */
	@Test
	void reviewPaths_noToken_unauthorizedNothingWritten() throws Exception {
		String listingId = pending();
		long version = dataVersion(listingId);
		Written before = written();
		String revision = LISTINGS + "/" + listingId + "/revisions/1";

		List<String> answers = List.of(codeOf(queue(null)), codeOf(review(null, listingId, "1")),
				codeOf(decide(null, listingId, "1", approval(version))),
				codeOf(postRaw(null, revision + "/decision", "decision")), codeOf(decisionRead(null, listingId, "1")),
				codeOf(openLocation(null, listingId, "1")), codeOf(copy(null, listingId, "1", version)),
				codeOf(postRaw(null, revision + "/copies", "copy")));

		assertEquals(Collections.nCopies(8, "401 UNAUTHORIZED"), answers);
		assertEquals(before, written());
	}

	/** E06S4-D2: a SALE (the assignee included) and a CUSTOMER get 403 on the queue and the review read. */
	@Test
	void queueAndReview_saleOrCustomer_forbidden() throws Exception {
		String listingId = pending();

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(sale, customer)) {
			answers.add(codeOf(queue(caller)));
			answers.add(codeOf(review(caller, listingId, "1")));
		}

		assertEquals(Collections.nCopies(4, "403 FORBIDDEN"), answers);
	}

	/** listing BR-5: a malformed or unknown listing id and a malformed or unknown number: one 404 body. */
	@Test
	void review_malformedOrUnknownIds_sameNotFound() throws Exception {
		String listingId = pending();

		List<Result> answers = new ArrayList<>();
		for (String id : List.of(UNKNOWN, listingId.toLowerCase(Locale.ROOT), "not-a-listing")) {
			answers.add(review(reviewer, id, "1"));
		}
		for (String number : List.of("0", "01", "1234567890", "9")) {
			answers.add(review(reviewer, listingId, number));
		}

		List<String> bodies = new ArrayList<>();
		for (Result answer : answers) {
			assertCode(404, "NOT_FOUND", answer);
			bodies.add(bodyWithoutCorrelation(answer));
		}
		assertEquals(1, bodies.stream().distinct().count(), bodies.toString());
	}

	/** E06S4-D2: the managers, the assignee SALE and a CUSTOMER get 403 on the decision; nothing written. */
	@Test
	void decide_managersOrSale_forbiddenNothingWritten() throws Exception {
		String listingId = pending();
		long version = dataVersion(listingId);
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(manager, leader, sale, customer)) {
			answers.add(codeOf(decide(caller, listingId, "1", approval(version))));
		}

		assertEquals(Collections.nCopies(4, "403 FORBIDDEN"), answers);
		assertEquals(before, written());
	}

	/** listing BR-5: a malformed or unknown listing id or number answers 404 to a reviewer; nothing written. */
	@Test
	void decide_malformedOrUnknownIds_notFound() throws Exception {
		String listingId = pending();
		long version = dataVersion(listingId);
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (String id : List.of(UNKNOWN, "not-a-listing")) {
			answers.add(codeOf(decide(reviewer, id, "1", approval(version))));
		}
		for (String number : List.of("0", "01", "1234567890", "9")) {
			answers.add(codeOf(decide(reviewer, listingId, number, approval(version))));
		}

		assertEquals(Collections.nCopies(6, "404 NOT_FOUND"), answers);
		assertEquals(before, written());
	}

	/** E06S4-D2: the managers get 403 on the location; no access log row. */
	@Test
	void unitLocation_managers_forbiddenNoLog() throws Exception {
		String listingId = pending();
		Written before = written();

		List<String> answers = List.of(codeOf(openLocation(manager, listingId, "1")),
				codeOf(openLocation(leader, listingId, "1")), codeOf(openLocation(customer, listingId, "1")));

		assertEquals(Collections.nCopies(3, "403 FORBIDDEN"), answers);
		assertEquals(before, written());
	}

	/** listing BR-5: unknown and malformed ids answer 404 to a reviewer; no access log row. */
	@Test
	void unitLocation_unknownIds_notFoundNoLog() throws Exception {
		String listingId = pending();
		Written before = written();

		List<String> answers = List.of(codeOf(openLocation(reviewer, UNKNOWN, "1")),
				codeOf(openLocation(reviewer, "not-a-listing", "1")), codeOf(openLocation(reviewer, listingId, "9")),
				codeOf(openLocation(reviewer, listingId, "0")));

		assertEquals(Collections.nCopies(4, "404 NOT_FOUND"), answers);
		assertEquals(before, written());
	}

	/** E06S4-D2: a CUSTOMER gets 403 on the decision read. */
	@Test
	void decisionRead_customer_forbidden() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", approval(dataVersion(listingId)));

		assertCode(403, "FORBIDDEN", decisionRead(customer, listingId, "1"));
	}

	/** E06S4-D14: the copy is the drafter's: a reviewer, an ADMIN and a manager get 403; nothing written. */
	@Test
	void copy_reviewerAdminOrManager_forbidden() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("OTHER"), NOTE));
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(reviewer, admin, manager)) {
			answers.add(codeOf(copy(caller, listingId, "1", dataVersion(listingId))));
		}

		assertEquals(Collections.nCopies(3, "403 FORBIDDEN"), answers);
		assertEquals(before, written());
	}

	/** listing BR-5, BR-16: another SALE and unknown ids get the one 404 on the copy; nothing written. */
	@Test
	void copy_otherSaleOrUnknownIds_notFound() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("OTHER"), NOTE));
		long version = dataVersion(listingId);
		Written before = written();

		List<String> answers = List.of(codeOf(copy(otherSale, listingId, "1", version)),
				codeOf(copy(sale, UNKNOWN, "1", version)), codeOf(copy(sale, listingId, "9", version)),
				codeOf(copy(sale, listingId, "01", version)));

		assertEquals(Collections.nCopies(4, "404 NOT_FOUND"), answers);
		assertEquals(before, written());
	}
}
