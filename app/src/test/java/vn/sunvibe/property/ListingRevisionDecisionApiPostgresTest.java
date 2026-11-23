package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * The decision over HTTP on PostgreSQL (listing-publish US-4 AC-3 to AC-5, BR-4, BR-6, BR-7, BR-13; E06-S4 spec 3.3,
 * 4.3, E06S4-D5 to D9, D20; RV-6 to RV-8, RV-12, RV-13): each outcome stored once with one history row, an approval that
 * publishes nothing and cancels a waiting one, every broken shape answered 400 before the role, the terms check of an
 * approval, the refusals in E06S4-D5's order with nothing written, and the note in no error body, history row or log
 * line. Needs Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class ListingRevisionDecisionApiPostgresTest extends ListingReviewApiTestBase {

	private static final String MARKER = "review-marker-0000";

	@Override
	String phonePrefix() {
		return "0906";
	}

	/** RV-6, RV-13: a return with two reasons and a note: 200 with the next version; one decision row; one history row. */
	@Test
	void decide_returnWithReasonsAndNote_storedOnceHistoryRow() throws Exception {
		String listingId = pending();
		long version = dataVersion(listingId);
		Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);

		Result answer = decide(reviewer, listingId, "1", returnBody(version, List.of("TERMS_MISMATCH",
				"MISSING_INFORMATION"), "  " + NOTE + "\r\n"));

		Instant after = Instant.now();
		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(List.of(listingId, "1", "NEEDS_CHANGES", String.valueOf(version + 1)),
				List.of(answer.body().path("listingId").asString(), answer.body().path("revisionNo").asString(),
						answer.body().path("status").asString(), answer.body().path("dataVersion").asString()));
		Map<String, Object> row = jdbc.queryForMap("select * from listing_revision_reviews where listing_id = ?",
				listingId);
		assertEquals(List.of("NEEDS_CHANGES", "MISSING_INFORMATION,TERMS_MISMATCH", NOTE, reviewer.userId()),
				List.of(row.get("outcome"), row.get("change_reasons"), row.get("note"), row.get("decided_by")));
		assertEquals(1, decisionRows(listingId));
		assertEquals("NEEDS_CHANGES", revisionStatus(listingId, 1));
		assertEquals("REVISION_RETURNED 1 " + reviewer.userId(), history(listingId).getLast());
		Instant occurred = jdbc.queryForObject("select occurred_at from listing_history where listing_id = ? and"
				+ " action = 'REVISION_RETURNED'", Timestamp.class, listingId).toInstant();
		assertFalse(occurred.isBefore(before) || occurred.isAfter(after), occurred.toString());
	}

	/** RV-6: a rejection with its group and note. */
	@Test
	void decide_reject_storedWithGroup() throws Exception {
		String listingId = pending();

		Result answer = decide(reviewer, listingId, "1", rejection(dataVersion(listingId), "SUSPECTED_FRAUD", NOTE));

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("REJECTED", answer.body().path("status").asString());
		Map<String, Object> row = jdbc.queryForMap("select * from listing_revision_reviews where listing_id = ?",
				listingId);
		assertEquals(List.of("REJECTED", "SUSPECTED_FRAUD", NOTE),
				List.of(row.get("outcome"), row.get("rejection_group"), row.get("note")));
		assertEquals("REJECTED", revisionStatus(listingId, 1));
		assertEquals("REVISION_REJECTED 1 " + reviewer.userId(), history(listingId).getLast());
	}

	/** RV-8: an approval with the five checks: APPROVED, checklist version 1; the listing stays UNPUBLISHED; no event. */
	@Test
	void decide_approveFiveChecks_approvedNothingPublic() throws Exception {
		String listingId = pending();
		int outbox = outboxCount();

		Result answer = decide(reviewer, listingId, "1", approval(dataVersion(listingId)));

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("APPROVED", answer.body().path("status").asString());
		assertEquals(1, jdbc.queryForObject("select checklist_version from listing_revision_reviews where listing_id = ?",
				Integer.class, listingId));
		assertEquals("UNPUBLISHED", jdbc.queryForObject("select state from listings where id = ?", String.class,
				listingId));
		assertEquals("UNPUBLISHED", getListing(manager, listingId).body().path("state").asString());
		assertEquals(outbox, outboxCount());
		assertEquals("REVISION_APPROVED 1 " + reviewer.userId(), history(listingId).getLast());
	}

	/** RV-8: revision 1 approved and waiting; revision 2 approved: 1 CANCELLED, 2 APPROVED, both rows by the decider. */
	@Test
	void decide_secondApproval_cancelsWaitingOne() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", approval(dataVersion(listingId)));
		secondSubmitted(listingId);

		Result answer = decide(reviewer, listingId, "2", approval(dataVersion(listingId)));

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals(List.of("CANCELLED", "APPROVED"), List.of(revisionStatus(listingId, 1), revisionStatus(listingId, 2)));
		// the two rows share the decision's time; their order is the aggregate's, not one the table keeps
		List<String> history = history(listingId);
		assertEquals(java.util.Set.of("APPROVAL_CANCELLED 1 " + reviewer.userId(),
				"REVISION_APPROVED 2 " + reviewer.userId()), java.util.Set.copyOf(history.subList(history.size() - 2,
				history.size())));
		assertEquals(2, decisionRows(listingId));
	}

	static Stream<Arguments> brokenShapes() {
		String sixUnknown = "[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]";
		String sixReasons = "[\"CONTENT_MISMATCH\",\"TEXT_RULE_VIOLATION\",\"MISSING_INFORMATION\",\"TERMS_MISMATCH\","
				+ "\"OTHER\",\"OTHER\"]";
		String five = "[\"CONTENT_MATCHES_UNIT\",\"TEXT_FOLLOWS_RULES\",\"TERMS_MATCH_AGREEMENT\",\"NO_FRAUD_SIGNS\","
				+ "\"AGREEMENT_END_AND_RIGHT\"]";
		String note = "\"Thiếu ảnh.\"";
		return Stream.of(
				Arguments.of("dataVersion missing", "{\"outcome\":\"APPROVED\",\"checks\":" + five + "}",
						"400 VALIDATION_FAILED [dataVersion]"),
				Arguments.of("outcome missing", "{\"dataVersion\":3,\"checks\":" + five + "}",
						"400 VALIDATION_FAILED [outcome]"),
				Arguments.of("outcome PUBLISHED", "{\"dataVersion\":3,\"outcome\":\"PUBLISHED\",\"checks\":" + five + "}",
						"400 VALIDATION_FAILED [outcome]"),
				Arguments.of("approved four checks", "{\"dataVersion\":3,\"outcome\":\"APPROVED\",\"checks\":"
						+ "[\"CONTENT_MATCHES_UNIT\",\"TEXT_FOLLOWS_RULES\",\"TERMS_MATCH_AGREEMENT\",\"NO_FRAUD_SIGNS\"]}",
						"400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("approved a check twice", "{\"dataVersion\":3,\"outcome\":\"APPROVED\",\"checks\":"
						+ "[\"CONTENT_MATCHES_UNIT\",\"TEXT_FOLLOWS_RULES\",\"TERMS_MATCH_AGREEMENT\",\"NO_FRAUD_SIGNS\","
						+ "\"NO_FRAUD_SIGNS\"]}", "400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("approved with a note", "{\"dataVersion\":3,\"outcome\":\"APPROVED\",\"checks\":" + five
						+ ",\"note\":" + note + "}", "400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("approved with reasons", "{\"dataVersion\":3,\"outcome\":\"APPROVED\",\"checks\":" + five
						+ ",\"changeReasons\":[\"OTHER\"]}", "400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("approved an unknown third check", "{\"dataVersion\":3,\"outcome\":\"APPROVED\",\"checks\":"
						+ "[\"CONTENT_MATCHES_UNIT\",\"TEXT_FOLLOWS_RULES\",\"PHOTOS_OK\",\"NO_FRAUD_SIGNS\","
						+ "\"AGREEMENT_END_AND_RIGHT\"]}", "400 VALIDATION_FAILED [checks[2]]"),
				Arguments.of("returned without reasons", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\",\"note\":"
						+ note + "}", "400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("returned with no reason", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\","
						+ "\"changeReasons\":[],\"note\":" + note + "}", "400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("returned a reason twice", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\","
						+ "\"changeReasons\":[\"OTHER\",\"OTHER\"],\"note\":" + note + "}",
						"400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("returned without a note", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\","
						+ "\"changeReasons\":[\"OTHER\"]}", "400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("returned a blank note", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\","
						+ "\"changeReasons\":[\"OTHER\"],\"note\":\"  \\r\\n \"}",
						"400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("returned a photo reason", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\","
						+ "\"changeReasons\":[\"PHOTO_NOT_ACCEPTED\"],\"note\":" + note + "}",
						"400 VALIDATION_FAILED [changeReasons[0]]"),
				Arguments.of("rejected without a group", "{\"dataVersion\":3,\"outcome\":\"REJECTED\",\"note\":" + note
						+ "}", "400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("rejected with checks", "{\"dataVersion\":3,\"outcome\":\"REJECTED\",\"rejectionGroup\":"
						+ "\"WRONG_UNIT\",\"checks\":" + five + ",\"note\":" + note + "}",
						"400 VALIDATION_FAILED [decisionMatchesOutcome]"),
				Arguments.of("rejected SPAM", "{\"dataVersion\":3,\"outcome\":\"REJECTED\",\"rejectionGroup\":\"SPAM\","
						+ "\"note\":" + note + "}", "400 VALIDATION_FAILED [rejectionGroup]"),
				Arguments.of("note of 1,001 code points", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\","
						+ "\"changeReasons\":[\"OTHER\"],\"note\":\"" + "a".repeat(1_001) + "\"}",
						"400 VALIDATION_FAILED [note]"),
				Arguments.of("note of 4,001 UTF-16 units", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\","
						+ "\"changeReasons\":[\"OTHER\"],\"note\":\"" + "a".repeat(4_001) + "\"}",
						"400 VALIDATION_FAILED [note]"),
				Arguments.of("note with U+0007", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\","
						+ "\"changeReasons\":[\"OTHER\"],\"note\":\"Thiếu\\u0007ảnh\"}", "400 VALIDATION_FAILED [note]"),
				Arguments.of("approved six checks", "{\"dataVersion\":3,\"outcome\":\"APPROVED\",\"checks\":"
						+ "[\"CONTENT_MATCHES_UNIT\",\"TEXT_FOLLOWS_RULES\",\"TERMS_MATCH_AGREEMENT\",\"NO_FRAUD_SIGNS\","
						+ "\"AGREEMENT_END_AND_RIGHT\",\"NO_FRAUD_SIGNS\"]}", "400 VALIDATION_FAILED [checks]"),
				Arguments.of("approved six unknown, no dataVersion", "{\"outcome\":\"APPROVED\",\"checks\":" + sixUnknown
						+ "}", "400 VALIDATION_FAILED [checks]"),
				Arguments.of("returned six reasons", "{\"dataVersion\":3,\"outcome\":\"NEEDS_CHANGES\",\"changeReasons\":"
						+ sixReasons + ",\"note\":" + note + "}", "400 VALIDATION_FAILED [changeReasons]"),
				Arguments.of("both lists six", "{\"dataVersion\":3,\"outcome\":\"APPROVED\",\"checks\":" + sixUnknown
						+ ",\"changeReasons\":" + sixReasons + "}", "400 VALIDATION_FAILED [changeReasons, checks]"),
				Arguments.of("not JSON", "decision", "400 MALFORMED_REQUEST []"),
				Arguments.of("outcome an object", "{\"dataVersion\":3,\"outcome\":{\"value\":\"APPROVED\"}}",
						"400 MALFORMED_REQUEST []"));
	}

	/** RV-6: every broken shape answers its code and exact entries before the role, to a reviewer and a SALE alike. */
	@ParameterizedTest(name = "{0}")
	@MethodSource("brokenShapes")
	void decide_brokenShape_badRequestEntriesBeforeRole(String name, String body, String expected) throws Exception {
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(reviewer, sale)) {
			Result answer = postRaw(caller, LISTINGS + "/" + UNKNOWN + "/revisions/1/decision", body);
			answers.add(codeOf(answer) + " " + fields(answer).stream().sorted().toList());
		}

		assertEquals(List.of(expected, expected), answers, name);
		assertEquals(before, written());
	}

	/** RV-6: a second decision of the same revision answers REVISION_NOT_PENDING; one decision row. */
	@Test
	void decide_secondDecision_revisionNotPending() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("OTHER"), NOTE));
		Written before = written();

		Result second = decide(reviewer, listingId, "1", approval(dataVersion(listingId)));

		assertCode(409, "REVISION_NOT_PENDING", second);
		assertEquals(before, written());
		assertEquals(1, decisionRows(listingId));
	}

	/**
	 * RV-7: the snapshot's rent changed by SQL equals neither version of E05: an approval answers
	 * REVISION_TERMS_MISMATCH with nothing written; a return with TERMS_MISMATCH answers 200.
	 */
	@Test
	void decide_termsEqualNeither_termsMismatchNothingWritten() throws Exception {
		String listingId = pending();
		jdbc.update("update listing_revisions set terms_monthly_rent = terms_monthly_rent + 1 where listing_id = ?",
				listingId);
		Written before = written();

		Result approval = decide(reviewer, listingId, "1", approval(dataVersion(listingId)));

		assertCode(409, "REVISION_TERMS_MISMATCH", approval);
		assertEquals(before, written());
		Result returned = decide(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("TERMS_MISMATCH"),
				NOTE));
		assertEquals(200, returned.status(), returned.rawBody());
	}

	/** RV-7: a snapshot taken from the version waiting to start equals it: approved. */
	@Test
	void decide_approvePendingStartSnapshot_approved() throws Exception {
		Drafting drafting = listingPendingStart();
		String listingId = drafting.listingId();
		assertEquals(200, saveContent(sale, listingId, "1", completeContentBody(dataVersion(listingId))).status());
		assertEquals(200, captureTerms(sale, listingId, "1", dataVersion(listingId), "PENDING_START").status());
		assertEquals(200, submit(sale, listingId, "1", dataVersion(listingId)).status());

		Result answer = decide(reviewer, listingId, "1", approval(dataVersion(listingId)));

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("APPROVED", revisionStatus(listingId, 1));
	}

	/**
	 * RV-13: after a return, a rejection, an approval that cancels a waiting one and a copy, each new history row holds
	 * its action, revision, actor and time, and no column of the table holds the note, a reason or a group.
	 */
	@Test
	void history_newActions_identifiersOnly() throws Exception {
		String returned = pending();
		decided(reviewer, returned, "1", returnBody(dataVersion(returned), List.of("MISSING_INFORMATION"), MARKER));
		assertEquals(201, copy(sale, returned, "1", dataVersion(returned)).status());
		String rejected = pending();
		decided(reviewer, rejected, "1", rejection(dataVersion(rejected), "WRONG_UNIT", MARKER));
		String approved = pending();
		decided(reviewer, approved, "1", approval(dataVersion(approved)));
		secondSubmitted(approved);
		decided(reviewer, approved, "2", approval(dataVersion(approved)));

		assertEquals(List.of("action", "actor_user_id", "id", "listing_id", "occurred_at", "revision_no"),
				jdbc.queryForList("select column_name from information_schema.columns where table_name ="
						+ " 'listing_history' and table_schema = current_schema()", String.class).stream().sorted()
						.toList());
		List<Map<String, Object>> rows = jdbc.queryForList("select * from listing_history where listing_id in (?, ?, ?)"
				+ " and action in ('REVISION_RETURNED', 'REVISION_REJECTED', 'APPROVAL_CANCELLED', 'REVISION_APPROVED',"
				+ " 'REVISION_COPIED')", returned, rejected, approved);
		List<String> actions = rows.stream().map(row -> row.get("action") + " " + row.get("revision_no")).sorted()
				.toList();
		assertEquals(List.of("APPROVAL_CANCELLED 1", "REVISION_APPROVED 1", "REVISION_APPROVED 2",
				"REVISION_COPIED 2", "REVISION_REJECTED 1", "REVISION_RETURNED 1"), actions);
		for (Map<String, Object> row : rows) {
			String actor = "REVISION_COPIED".equals(row.get("action")) ? sale.userId() : reviewer.userId();
			assertEquals(actor, row.get("actor_user_id"), row.toString());
			assertTrue(row.get("occurred_at") != null, row.toString());
		}
		List<String> texts = jdbc.queryForList("select h::text from listing_history h where listing_id in (?, ?, ?)",
				String.class, returned, rejected, approved);
		List<String> holders = texts.stream().filter(text -> text.contains(MARKER) || text.contains("MISSING_INFORMATION")
				|| text.contains("WRONG_UNIT") || text.contains("CONTENT_MATCHES_UNIT")).toList();
		assertEquals(List.of(), holders);
	}

	/** RV-11, listing BR-3: a stale data version answers CONCURRENT_MODIFICATION; nothing written. */
	@Test
	void decide_staleDataVersion_concurrentModification() throws Exception {
		String listingId = pending();
		Written before = written();

		Result answer = decide(reviewer, listingId, "1", approval(dataVersion(listingId) - 1));

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals(before, written());
		assertEquals("PENDING_REVIEW", revisionStatus(listingId, 1));
	}

	/** RV-12: a pending revision of an ended agreement answers LISTING_ARCHIVED; nothing written. */
	@Test
	void decide_endedAgreement_listingArchivedNothingWritten() throws Exception {
		Drafting drafting = pendingDrafting();
		Result termination = terminate(sale, drafting.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		Written before = written();

		Result answer = decide(reviewer, drafting.listingId(), "1", approval(dataVersion(drafting.listingId())));

		assertCode(409, "LISTING_ARCHIVED", answer);
		assertEquals(before, written());
	}

	/** RV-13, E06S4-D20: a marker note through a 200, a 400 by another field and a 409: in no error body, no log line. */
	@Test
	void decide_note_neverInErrorBodyOrLog(CapturedOutput output) throws Exception {
		String listingId = pending();
		long version = dataVersion(listingId);

		Result invalid = decide(reviewer, listingId, "1", returnBody(version, List.of("PHOTO_NOT_ACCEPTED"), MARKER));
		Result returned = decide(reviewer, listingId, "1", returnBody(version, List.of("OTHER"), MARKER));
		Result again = decide(reviewer, listingId, "1", returnBody(version + 1, List.of("OTHER"), MARKER));

		assertEquals(List.of("400 VALIDATION_FAILED", "200", "409 REVISION_NOT_PENDING"),
				List.of(codeOf(invalid), String.valueOf(returned.status()), codeOf(again)));
		List<String> holders = new ArrayList<>();
		if (invalid.rawBody().contains(MARKER)) {
			holders.add("400 body");
		}
		if (again.rawBody().contains(MARKER)) {
			holders.add("409 body");
		}
		if (output.getAll().contains(MARKER)) {
			holders.add("log");
		}
		assertEquals(List.of(), holders);
	}

	/** Revision 2 started beside the decided revision 1, completed and submitted by {@code sale}. */
	private void secondSubmitted(String listingId) throws Exception {
		Result started = startRevision(sale, listingId, dataVersion(listingId));
		assertEquals(201, started.status(), started.rawBody());
		assertEquals(200, saveContent(sale, listingId, "2", completeContentBody(dataVersion(listingId))).status());
		assertEquals(200, captureTerms(sale, listingId, "2", dataVersion(listingId), "IN_EFFECT").status());
		Result submitted = submit(sale, listingId, "2", dataVersion(listingId));
		assertEquals(200, submitted.status(), submitted.rawBody());
	}
}
