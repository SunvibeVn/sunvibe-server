package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * The decision read over HTTP on PostgreSQL (listing-publish US-4 AC-6; listing BR-5; E06-S4 spec 3.5, 4.4, E06S4-D15;
 * RV-9): the assigned Sale, a manager and a reviewer read the outcome, the reasons and the note; a SALE who is not the
 * assignee, an unknown listing or revision and an undecided revision get the one 404; no user id is answered. Needs
 * Docker.
 */
class ListingRevisionDecisionReadApiPostgresTest extends ListingReviewApiTestBase {

	@Override
	String phonePrefix() {
		return "0904";
	}

	/** RV-9: the assignee, a manager and a reviewer read the same decision with its reasons and note. */
	@Test
	void decision_assigneeManagerReviewer_reasonsAndNote() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("OTHER", "CONTENT_MISMATCH"),
				NOTE));

		List<JsonNode> bodies = new ArrayList<>();
		for (TestUser caller : List.of(sale, manager, reviewer)) {
			Result answer = decisionRead(caller, listingId, "1");
			assertEquals(200, answer.status(), answer.rawBody());
			bodies.add(answer.body());
		}

		JsonNode body = bodies.getFirst();
		assertEquals(List.of("NEEDS_CHANGES", "true", "[\"CONTENT_MISMATCH\",\"OTHER\"]", "true", NOTE),
				List.of(body.path("outcome").asString(), String.valueOf(body.path("checklistVersion").isNull()),
						body.path("changeReasons").toString(), String.valueOf(body.path("rejectionGroup").isNull()),
						body.path("note").asString()));
		assertEquals(jdbc.queryForObject("select decided_at from listing_revision_reviews where listing_id = ?",
				java.sql.Timestamp.class, listingId).toInstant(), java.time.Instant.parse(body.path("decidedAt")
				.asString()));
		assertEquals(List.of(body, body), bodies.subList(1, 3));
	}

	/** RV-9, listing BR-5: a SALE not the assignee, unknown ids and an undecided revision: one 404 body. */
	@Test
	void decision_saleNotAssigneeUnknownOrUndecided_sameNotFound() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", rejection(dataVersion(listingId), "WRONG_UNIT", NOTE));
		String undecided = pending();

		List<Result> answers = List.of(decisionRead(otherSale, listingId, "1"), decisionRead(reviewer, UNKNOWN, "1"),
				decisionRead(reviewer, listingId, "9"), decisionRead(reviewer, listingId, "01"),
				decisionRead(reviewer, undecided, "1"), decisionRead(sale, undecided, "1"));

		List<String> bodies = new ArrayList<>();
		for (Result answer : answers) {
			assertCode(404, "NOT_FOUND", answer);
			bodies.add(bodyWithoutCorrelation(answer));
		}
		assertEquals(1, bodies.stream().distinct().count(), bodies.toString());
	}

	/** RV-9, E06S4-D15: the body holds the six fields and no user id. */
	@Test
	void decision_body_noUserId() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", approval(dataVersion(listingId)));

		Result answer = decisionRead(sale, listingId, "1");

		assertEquals(200, answer.status(), answer.rawBody());
		List<String> names = new ArrayList<>();
		answer.body().properties().forEach(property -> names.add(property.getKey()));
		assertEquals(List.of("changeReasons", "checklistVersion", "decidedAt", "note", "outcome", "rejectionGroup"),
				names.stream().sorted().toList());
		assertEquals(1, answer.body().path("checklistVersion").asInt());
		assertFalse(answer.rawBody().contains(reviewer.userId()), answer.rawBody());
		assertFalse(answer.rawBody().contains(sale.userId()), answer.rawBody());
	}
}
