package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * The freshness record, the freshness read and the listings to reconfirm over HTTP on PostgreSQL (listing-publish US-6
 * AC-1 to AC-3, AC-6, AC-7, BR-9 to BR-11, BR-17; listing BR-3, BR-5, BR-16; E06-S3 spec 3.1 to 3.3, E06S3-D1 to
 * E06S3-D15; FR-1 to FR-11): only the assigned Sale records, dated today in Vietnam by the server; the body rules
 * answer 400 before the role; other roles 403 on every id; anyone else in scope 404; an archived listing or a stale
 * data version 409; the note reaches no error body and no log line. The bases' users; the SUPER_ADMIN and the legacy
 * roles are made where needed (plan E06S3-P11). A record made here is accepted as dated the Vietnam date read just
 * before or just after the call (E06S3-D14). Needs Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class ListingFreshnessApiPostgresTest extends ListingRevisionApiTestBase {

	private static final String NOTE = "Chủ xác nhận còn trống.\nGọi lại sau 10 ngày.";
	private static final String MARKER = "freshness-marker-0000";

	@Override
	String phonePrefix() {
		return "0916";
	}

	/** testing.md: no token, 401 UNAUTHORIZED on the record, the freshness read and the list; nothing written. */
	@Test
	void freshnessPaths_noToken_unauthorizedNothingWritten() throws Exception {
		String listingId = listingInEffect().listingId();
		Written before = written();

		List<String> answers = List.of(codeOf(record(null, listingId, available(dataVersion(listingId)))),
				codeOf(freshness(null, listingId)), codeOf(reconfirmations(null)));

		assertEquals(Collections.nCopies(3, "401 UNAUTHORIZED"), answers);
		assertEquals(before, written());
	}

	/** FR-1, FR-7, FR-11: the assigned Sale records; 201 with the next data version; the read answers the dates. */
	@Test
	void record_assignedSaleAvailable_createdDatedTodayAndRead() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);

		LocalDate dayBefore = today();
		Result answer = record(sale, listingId, body(version, "CALL", "AVAILABLE", true, true, NOTE));
		LocalDate dayAfter = today();

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(List.of(listingId, version + 1, true), List.of(answer.body().path("listingId").asString(),
				answer.body().path("dataVersion").asLong(), answer.body().path("valid").asBoolean()));
		JsonNode read = okFreshness(sale, listingId);
		LocalDate accepted = LocalDate.parse(read.path("confirmedOn").asString());
		assertTrue(List.of(dayBefore, dayAfter).contains(accepted), read.toString());
		assertEquals(List.of(accepted.plusDays(10).toString(), accepted.plusDays(14).toString(), "false", "false"),
				List.of(read.path("reconfirmFrom").asString(), read.path("validThrough").asString(),
						read.path("reconfirmationDue").asString(), read.path("deadlinePassed").asString()));
		JsonNode latest = read.path("latest");
		assertEquals(List.of(accepted.toString(), "CALL", "AVAILABLE", "true", "true", "true", NOTE),
				List.of(latest.path("contactDate").asString(), latest.path("channel").asString(),
						latest.path("outcome").asString(), latest.path("termsStillApply").asString(),
						latest.path("unitUnchanged").asString(), latest.path("valid").asString(),
						latest.path("note").asString()));
	}

	/** FR-1: a date sent in the body is ignored; the server dates the contact. */
	@Test
	void record_dateInBody_ignored() throws Exception {
		String listingId = listingInEffect().listingId();
		Map<String, Object> body = available(dataVersion(listingId));
		body.put("contactDate", "2020-01-01");
		body.put("recordedAt", "2020-01-01T00:00:00Z");

		LocalDate dayBefore = today();
		Result answer = record(sale, listingId, body);
		LocalDate dayAfter = today();

		assertEquals(201, answer.status(), answer.rawBody());
		LocalDate stored = jdbc.queryForObject("select contact_date from listing_freshness_records where listing_id = ?",
				Date.class, listingId).toLocalDate();
		assertTrue(List.of(dayBefore, dayAfter).contains(stored), stored.toString());
	}

	/** FR-2: the answers must match the outcome; 400 answersMatchOutcome before the role check; nothing written. */
	@Test
	void record_answersNotMatchingOutcome_badRequestBeforeRole() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (Map<String, Object> body : List.of(body(version, "CALL", "AVAILABLE", true, null, null),
				body(version, "CALL", "UNREACHABLE", true, null, null))) {
			for (TestUser caller : List.of(sale, manager, otherSale)) {
				answers.add(codeAndFields(record(caller, listingId, body)));
			}
		}

		assertEquals(Collections.nCopies(6, "400 VALIDATION_FAILED [answersMatchOutcome]"), answers);
		assertEquals(before, written());
	}

	/**
	 * FR-2: a note over 2,000 UTF-16 units (also one normalization would shorten), over 500 code points, with NUL or a
	 * format character: 400 with the one entry note, before the role check; nothing written.
	 */
	@Test
	void record_noteBounds_badRequestNoteOnlyBeforeRole() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (String note : List.of("a".repeat(2001), "a" + "\r\n".repeat(1000), "a".repeat(501), "a\u0000b",
				"a​b")) {
			for (TestUser caller : List.of(sale, manager, otherSale)) {
				answers.add(codeAndFields(record(caller, listingId, body(version, "CALL", "AVAILABLE", true, true,
						note))));
			}
		}

		assertEquals(Collections.nCopies(15, "400 VALIDATION_FAILED [note]"), answers);
		assertEquals(before, written());
	}

	/**
	 * FR-2, E06S3-D15: an unknown channel or outcome, a missing or negative data version: 400 with the field; an answer
	 * of another type: MALFORMED_REQUEST; each before the role check; nothing written.
	 */
	@Test
	void record_channelOutcomeVersionOrType_badRequestBeforeRole() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		Map<String, Object> noVersion = available(version);
		noVersion.remove("dataVersion");
		Map<String, Object> wrongType = available(version);
		wrongType.put("termsStillApply", "yes");
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (Map<String, Object> body : List.of(body(version, "FAX", "AVAILABLE", true, true, null),
				body(version, "CALL", "MAYBE", null, null, null), noVersion, available(-1), wrongType)) {
			for (TestUser caller : List.of(sale, manager, otherSale)) {
				answers.add(codeAndFields(record(caller, listingId, body)));
			}
		}

		List<String> expected = new ArrayList<>();
		for (String answer : List.of("400 VALIDATION_FAILED [channel]", "400 VALIDATION_FAILED [outcome]",
				"400 VALIDATION_FAILED [dataVersion]", "400 VALIDATION_FAILED [dataVersion]", "400 MALFORMED_REQUEST []")) {
			expected.addAll(Collections.nCopies(3, answer));
		}
		assertEquals(expected, answers);
		assertEquals(before, written());
	}

	/** E06S3-D10: a note of white space and line breaks only is no note. */
	@Test
	void record_whitespaceNote_storedAsNull() throws Exception {
		String listingId = listingInEffect().listingId();

		Result answer = record(sale, listingId, body(dataVersion(listingId), "MESSAGE", "UNREACHABLE", null, null,
				" \r\n\t \n "));

		assertEquals(201, answer.status(), answer.rawBody());
		assertNull(jdbc.queryForObject("select note from listing_freshness_records where listing_id = ?", String.class,
				listingId));
	}

	/** FR-3, BR-11: after a valid confirmation 8 days back, each invalid result keeps its date and marks it invalid. */
	@Test
	void record_invalidOutcomes_keepDateLatestInvalid() throws Exception {
		String listingId = listingInEffect().listingId();
		LocalDate confirmedOn = today().minusDays(8);
		confirmedBySql(listingId, confirmedOn, true);

		List<List<String>> reads = new ArrayList<>();
		for (Map<String, Object> body : List.of(body(0, "CALL", "NOT_AVAILABLE", null, null, null),
				body(0, "IN_PERSON", "UNREACHABLE", null, null, null), body(0, "CALL", "AVAILABLE", true, false, null))) {
			body.put("dataVersion", dataVersion(listingId));
			Result answer = record(sale, listingId, body);
			assertEquals(201, answer.status(), answer.rawBody());
			JsonNode read = okFreshness(sale, listingId);
			reads.add(List.of(read.path("confirmedOn").asString(), read.path("latest").path("valid").asString()));
		}

		List<String> keptInvalid = List.of(confirmedOn.toString(), "false");
		assertEquals(List.of(keptInvalid, keptInvalid, keptInvalid), reads);
	}

	/** FR-8: SALES_MANAGER, ADMIN, SUPER_ADMIN, LISTING_REVIEWER, CUSTOMER, LEADER, MANAGER: 403 on every id. */
	@Test
	void record_otherRoles_forbiddenEveryId() throws Exception {
		String listingId = listingInEffect().listingId();
		TestUser superAdmin = survey.user("Freshness Super Admin", "SUPER_ADMIN");
		TestUser oldLeader = survey.user("Freshness Old Leader", "LEADER");
		TestUser oldManager = survey.user("Freshness Old Manager", "MANAGER");
		long version = dataVersion(listingId);
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(manager, admin, superAdmin, reviewer, customer, oldLeader, oldManager)) {
			for (String id : List.of(listingId, UNKNOWN, "01")) {
				answers.add(codeOf(record(caller, id, available(version))));
			}
		}

		assertEquals(Collections.nCopies(21, "403 FORBIDDEN"), answers);
		assertEquals(before, written());
	}

	/** FR-8, listing BR-5: another SALE, a SALE_LEADER not the assignee, an unknown and a malformed id: one 404 body. */
	@Test
	void record_notAssigneeUnknownMalformed_sameNotFound() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		Written before = written();

		List<Result> answers = List.of(record(otherSale, listingId, available(version)),
				record(leader, listingId, available(version)), record(sale, UNKNOWN, available(version)),
				record(sale, "01", available(version)));

		assertEquals(Collections.nCopies(4, "404 NOT_FOUND"), answers.stream().map(ListingFreshnessApiPostgresTest::codeOf)
				.toList());
		assertEquals(Collections.nCopies(4, bodyWithoutCorrelation(answers.getFirst())), answers.stream()
				.map(ListingFreshnessApiPostgresTest::bodyWithoutCorrelation).toList());
		assertEquals(before, written());
	}

	/** listing BR-16: after a reassignment the new assignee records and the former one gets 404. */
	@Test
	void record_reassignedLead_newAssigneeRecordsFormerNotFound() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		fixture.checklists().reassign(leader, drafting.chain().leadId(), otherSale);

		Result byNewAssignee = record(otherSale, listingId, available(dataVersion(listingId)));
		Result byFormer = record(sale, listingId, available(dataVersion(listingId)));

		assertEquals(201, byNewAssignee.status(), byNewAssignee.rawBody());
		assertCode(404, "NOT_FOUND", byFormer);
	}

	/** FR-9, US-9 AC-3: an agreement ended by E05 and a stored archive: 409 LISTING_ARCHIVED; nothing written. */
	@Test
	void record_archivedOrAgreementEnded_listingArchived() throws Exception {
		Drafting ended = listingInEffect();
		Result termination = terminate(sale, ended.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		String storedArchive = listingInEffect().listingId();
		jdbc.update("update listings set state = 'ARCHIVED' where id = ?", storedArchive);
		Written before = written();

		List<String> answers = List.of(
				codeOf(record(sale, ended.listingId(), available(dataVersion(ended.listingId())))),
				codeOf(record(sale, storedArchive, available(dataVersion(storedArchive)))));

		assertEquals(Collections.nCopies(2, "409 LISTING_ARCHIVED"), answers);
		assertEquals(before, written());
	}

	/** FR-9, listing BR-3: a data version another command moved answers 409 CONCURRENT_MODIFICATION; nothing written. */
	@Test
	void record_staleDataVersion_concurrentModification() throws Exception {
		String listingId = listingInEffect().listingId();
		long read = dataVersion(listingId);
		Result saved = saveContent(sale, listingId, "1", completeContentBody(read));
		assertEquals(200, saved.status(), saved.rawBody());
		Written before = written();

		Result answer = record(sale, listingId, available(read));

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals(before, written());
	}

	/**
	 * FR-10, E06S3-D3, E06S3-D9: a record moves the data version by one, leaves the state, writes one history row
	 * FRESHNESS_RECORDED with actor and time and no revision, and appends no event.
	 */
	@Test
	void record_movesDataVersion_stateUnchangedOneHistoryRow() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);
		int outboxBefore = listingRows().outbox();

		Result answer = record(sale, listingId, available(version));

		assertEquals(201, answer.status(), answer.rawBody());
		Result listing = getListing(sale, listingId);
		assertEquals(version + 1, listing.body().path("dataVersion").asLong(), listing.rawBody());
		assertEquals(List.of("UNPUBLISHED", "UNPUBLISHED"), List.of(listing.body().path("state").asString(),
				jdbc.queryForObject("select state from listings where id = ?", String.class, listingId)));
		Timestamp recordedAt = jdbc.queryForObject("select recorded_at from listing_freshness_records where listing_id = ?",
				Timestamp.class, listingId);
		assertEquals(List.of(sale.userId() + " null " + recordedAt.toInstant()), jdbc.query("select actor_user_id,"
				+ " revision_no, occurred_at from listing_history where listing_id = ? and action = 'FRESHNESS_RECORDED'",
				(row, rowNum) -> row.getString("actor_user_id") + " " + row.getObject("revision_no") + " "
						+ row.getTimestamp("occurred_at").toInstant(), listingId));
		assertEquals(outboxBefore, listingRows().outbox());
	}

	/** FR-9, E06S3-D6: two records the same day are two rows; the latest is the one with the greater row version. */
	@Test
	void record_twiceSameDay_twoRowsLatestBySecond() throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);

		Result first = record(sale, listingId, body(version, "CALL", "UNREACHABLE", null, null, null));
		Result second = record(sale, listingId, body(version + 1, "MESSAGE", "AVAILABLE", true, true, null));

		assertEquals(List.of(201, 201), List.of(first.status(), second.status()), second.rawBody());
		assertEquals(List.of(version + 1, version + 2), jdbc.queryForList("select listing_row_version from"
				+ " listing_freshness_records where listing_id = ? order by listing_row_version", Long.class, listingId));
		JsonNode latest = okFreshness(sale, listingId).path("latest");
		assertEquals(List.of("MESSAGE", "AVAILABLE"), List.of(latest.path("channel").asString(),
				latest.path("outcome").asString()));
	}

	/** FR-10, BR-17, plan E06S3-P7: a note's marker in no error body and no log line after a 201, a 400 and a 409. */
	@Test
	void record_note_neverInErrorBodyOrLog(CapturedOutput output) throws Exception {
		String listingId = listingInEffect().listingId();
		long version = dataVersion(listingId);

		Result created = record(sale, listingId, body(version, "CALL", "AVAILABLE", true, true, MARKER));
		Result invalid = record(sale, listingId, body(version + 1, "FAX", "AVAILABLE", true, true, MARKER));
		Result stale = record(sale, listingId, body(version, "CALL", "AVAILABLE", true, true, MARKER));

		assertEquals(List.of("201", "400 VALIDATION_FAILED", "409 CONCURRENT_MODIFICATION"),
				List.of(String.valueOf(created.status()), codeOf(invalid), codeOf(stale)));
		List<String> holders = new ArrayList<>();
		if (invalid.rawBody().contains(MARKER)) {
			holders.add("400 body");
		}
		if (stale.rawBody().contains(MARKER)) {
			holders.add("409 body");
		}
		if (output.getAll().contains(MARKER)) {
			holders.add("captured output");
		}
		assertEquals(List.of(), holders);
	}

	/** FR-6, E06S3-D3: a record writes no row of property-service or brokerage-service. */
	@Test
	void record_otherModules_untouched() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		List<Object> before = otherModules(drafting.chain());

		Result answer = record(sale, listingId, available(dataVersion(listingId)));

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(before, otherModules(drafting.chain()));
	}

	/** FR-8, FR-11: the five roles of getListing in scope read the latest record with its note and no user id. */
	@Test
	void freshness_readersInScope_latestNoteNoUserId() throws Exception {
		String listingId = listingInEffect().listingId();
		Result recorded = record(sale, listingId, body(dataVersion(listingId), "CALL", "AVAILABLE", true, true, NOTE));
		assertEquals(201, recorded.status(), recorded.rawBody());

		List<String> reads = new ArrayList<>();
		for (TestUser caller : List.of(sale, leader, manager, admin, reviewer)) {
			Result read = freshness(caller, listingId);
			reads.add(read.status() + " " + NOTE.equals(read.body().path("latest").path("note").asString()) + " "
					+ read.rawBody().contains(sale.userId()));
		}

		assertEquals(Collections.nCopies(5, "200 true false"), reads);
	}

	/** Spec section 4: CUSTOMER, LEADER and MANAGER get 403 on the freshness read for every id. */
	@Test
	void freshness_otherRoles_forbiddenEveryId() throws Exception {
		String listingId = listingInEffect().listingId();
		TestUser oldLeader = survey.user("Freshness Old Leader", "LEADER");
		TestUser oldManager = survey.user("Freshness Old Manager", "MANAGER");

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(customer, oldLeader, oldManager)) {
			for (String id : List.of(listingId, UNKNOWN, "01")) {
				answers.add(codeOf(freshness(caller, id)));
			}
		}

		assertEquals(Collections.nCopies(9, "403 FORBIDDEN"), answers);
	}

	/** E06S1-D2: another SALE on the listing, an unknown and a malformed id: one 404 body. */
	@Test
	void freshness_otherSaleMalformedOrUnknown_sameNotFound() throws Exception {
		String listingId = listingInEffect().listingId();

		List<Result> answers = List.of(freshness(otherSale, listingId), freshness(sale, UNKNOWN), freshness(sale, "01"));

		assertEquals(Collections.nCopies(3, "404 NOT_FOUND"), answers.stream()
				.map(ListingFreshnessApiPostgresTest::codeOf).toList());
		assertEquals(Collections.nCopies(3, bodyWithoutCorrelation(answers.getFirst())), answers.stream()
				.map(ListingFreshnessApiPostgresTest::bodyWithoutCorrelation).toList());
	}

	/** FR-11: before any record every date and the latest record are null and both flags false. */
	@Test
	void freshness_beforeAnyRecord_nullsAndFalse() throws Exception {
		String listingId = listingInEffect().listingId();

		JsonNode read = okFreshness(sale, listingId);

		assertEquals(List.of(true, true, true, false, false, true), List.of(read.path("confirmedOn").isNull(),
				read.path("reconfirmFrom").isNull(), read.path("validThrough").isNull(),
				read.path("reconfirmationDue").asBoolean(), read.path("deadlinePassed").asBoolean(),
				read.path("latest").isNull()));
	}

	/**
	 * FR-5: confirmations 8, 11, 13 and 16 days back (dates from the JVM clock): the three due listings oldest first,
	 * the 8-day one not listed, the 16-day one past its deadline, each with its dates and whether its latest is valid.
	 */
	@Test
	void reconfirmations_fixtureDays_dueOnlyOldestFirst() throws Exception {
		String eight = listingInEffect().listingId();
		String eleven = listingInEffect().listingId();
		String thirteen = listingInEffect().listingId();
		String sixteen = listingInEffect().listingId();
		LocalDate today = today();
		confirmedBySql(eight, today.minusDays(8), true);
		confirmedBySql(eleven, today.minusDays(11), true);
		confirmedBySql(thirteen, today.minusDays(13), false);
		confirmedBySql(sixteen, today.minusDays(16), true);

		JsonNode items = okReconfirmations(sale).path("items");

		List<String> due = List.of(sixteen, thirteen, eleven);
		assertEquals(due, ids(items).stream().filter(due::contains).toList());
		assertFalse(ids(items).contains(eight), items.toString());
		List<String> details = new ArrayList<>();
		items.forEach(item -> {
			if (due.contains(item.path("listingId").asString())) {
				details.add(item.path("confirmedOn").asString() + " " + item.path("reconfirmFrom").asString() + " "
						+ item.path("validThrough").asString() + " " + item.path("deadlinePassed").asBoolean() + " "
						+ item.path("latestRecordValid").asBoolean() + " " + item.path("state").asString());
			}
		});
		assertEquals(List.of(
				detail(today.minusDays(16), true, true),
				detail(today.minusDays(13), false, false),
				detail(today.minusDays(11), false, true)), details);
	}

	/**
	 * FR-5, E06S3-D2: the assigned SALE sees its listing; another SALE does not; SALE_LEADER, SALES_MANAGER, ADMIN and
	 * SUPER_ADMIN see it; LISTING_REVIEWER, CUSTOMER, LEADER and MANAGER get 403.
	 */
	@Test
	void reconfirmations_saleScopeManagersEveryOneReviewerForbidden() throws Exception {
		String listingId = listingInEffect().listingId();
		confirmedBySql(listingId, today().minusDays(16), true);
		TestUser superAdmin = survey.user("Freshness Super Admin", "SUPER_ADMIN");
		TestUser oldLeader = survey.user("Freshness Old Leader", "LEADER");
		TestUser oldManager = survey.user("Freshness Old Manager", "MANAGER");

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(sale, otherSale, leader, manager, admin, superAdmin, reviewer, customer, oldLeader,
				oldManager)) {
			Result answer = reconfirmations(caller);
			answers.add(answer.status() == 200 ? "200 " + ids(answer.body().path("items")).contains(listingId)
					: codeOf(answer));
		}

		assertEquals(List.of("200 true", "200 false", "200 true", "200 true", "200 true", "200 true", "403 FORBIDDEN",
				"403 FORBIDDEN", "403 FORBIDDEN", "403 FORBIDDEN"), answers);
	}

	/** FR-5: beside a due listing, one never confirmed, one stored archived and one whose agreement ended are left out. */
	@Test
	void reconfirmations_neverConfirmedArchivedOrEnded_leftOut() throws Exception {
		LocalDate due = today().minusDays(16);
		String listed = listingInEffect().listingId();
		confirmedBySql(listed, due, true);
		String neverConfirmed = listingInEffect().listingId();
		String storedArchive = listingInEffect().listingId();
		confirmedBySql(storedArchive, due, true);
		jdbc.update("update listings set state = 'ARCHIVED' where id = ?", storedArchive);
		Drafting ended = listingInEffect();
		confirmedBySql(ended.listingId(), due, true);
		Result termination = terminate(sale, ended.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());

		List<String> ids = ids(okReconfirmations(manager).path("items"));

		assertTrue(ids.contains(listed), ids.toString());
		assertFalse(ids.contains(neverConfirmed), "never confirmed " + ids);
		assertFalse(ids.contains(storedArchive), "stored archived " + ids);
		assertFalse(ids.contains(ended.listingId()), "agreement ended " + ids);
	}

	/** Spec section 3.3: a listing validly confirmed today is not listed, to its SALE or to a manager. */
	@Test
	void reconfirmations_confirmedToday_notListed() throws Exception {
		String listingId = listingInEffect().listingId();
		Result recorded = record(sale, listingId, available(dataVersion(listingId)));
		assertEquals(201, recorded.status(), recorded.rawBody());

		List<Boolean> listed = List.of(ids(okReconfirmations(sale).path("items")).contains(listingId),
				ids(okReconfirmations(manager).path("items")).contains(listingId));

		assertEquals(List.of(false, false), listed);
	}

	// ==================== the three calls over HTTP ====================

	private Result record(TestUser caller, String listingId, Map<String, Object> body) throws Exception {
		return api.post(caller, LISTINGS + "/" + listingId + "/freshness-records", body);
	}

	private Result freshness(TestUser caller, String listingId) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId + "/freshness");
	}

	private Result reconfirmations(TestUser caller) throws Exception {
		return api.get(caller, LISTINGS + "/reconfirmations");
	}

	private JsonNode okFreshness(TestUser caller, String listingId) throws Exception {
		Result read = freshness(caller, listingId);
		assertEquals(200, read.status(), read.rawBody());
		return read.body();
	}

	private JsonNode okReconfirmations(TestUser caller) throws Exception {
		Result read = reconfirmations(caller);
		assertEquals(200, read.status(), read.rawBody());
		return read.body();
	}

	/** A record body with every property present, nulls included. */
	private static Map<String, Object> body(Object dataVersion, Object channel, Object outcome, Object termsStillApply,
			Object unitUnchanged, Object note) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("dataVersion", dataVersion);
		body.put("channel", channel);
		body.put("outcome", outcome);
		body.put("termsStillApply", termsStillApply);
		body.put("unitUnchanged", unitUnchanged);
		body.put("note", note);
		return body;
	}

	/** Still available with both answers yes, no note: a valid confirmation. */
	private static Map<String, Object> available(Object dataVersion) {
		return body(dataVersion, "CALL", "AVAILABLE", true, true, null);
	}

	// ==================== fixtures and what a call wrote ====================

	/**
	 * A valid confirmation on that date written by SQL as a record writes it: its record row on the next row version,
	 * the two columns and the row version moved; with {@code latestValid} false, a later invalid record on top.
	 */
	private void confirmedBySql(String listingId, LocalDate confirmedOn, boolean latestValid) {
		long version = dataVersion(listingId) + 1;
		jdbc.update("insert into listing_freshness_records (id, listing_id, listing_row_version, contact_date, recorded_at,"
				+ " recorded_by, channel, outcome, terms_still_apply, unit_unchanged, valid, note) values (?, ?, ?, ?, ?, ?,"
				+ " 'CALL', 'AVAILABLE', true, true, true, null)", ids.generateId(), listingId, version,
				Date.valueOf(confirmedOn), Timestamp.from(Instant.now()), sale.userId());
		jdbc.update("update listings set freshness_confirmed_on = ?, freshness_latest_valid = true, row_version = ?"
				+ " where id = ?", Date.valueOf(confirmedOn), version, listingId);
		if (!latestValid) {
			jdbc.update("insert into listing_freshness_records (id, listing_id, listing_row_version, contact_date,"
					+ " recorded_at, recorded_by, channel, outcome, terms_still_apply, unit_unchanged, valid, note) values"
					+ " (?, ?, ?, ?, ?, ?, 'CALL', 'UNREACHABLE', null, null, false, null)", ids.generateId(), listingId,
					version + 1, Date.valueOf(confirmedOn.plusDays(1)), Timestamp.from(Instant.now()), sale.userId());
			jdbc.update("update listings set freshness_latest_valid = false, row_version = ? where id = ?", version + 1,
					listingId);
		}
	}

	/** The listing tables, the outbox and the freshness records: what a refused call leaves as it is. */
	private record Written(ListingRows rows, int records) {
	}

	private Written written() {
		return new Written(listingRows(), count("select count(*) from listing_freshness_records"));
	}

	/** The unit row and the agreement row read whole as text, and the count of the agreement's events. */
	private List<Object> otherModules(Chain chain) {
		return List.of(jdbc.queryForObject("select u::text from units u where id = ?", String.class, chain.unitId()),
				jdbc.queryForObject("select a::text from brokerage_agreements a where id = ?", String.class,
						chain.agreementId()),
				count("select count(*) from brokerage_agreement_events where agreement_id = ?", chain.agreementId()));
	}

	private static String detail(LocalDate confirmedOn, boolean deadlinePassed, boolean latestValid) {
		return confirmedOn + " " + confirmedOn.plusDays(10) + " " + confirmedOn.plusDays(14) + " " + deadlinePassed + " "
				+ latestValid + " UNPUBLISHED";
	}

	private static List<String> ids(JsonNode items) {
		List<String> ids = new ArrayList<>();
		items.forEach(item -> ids.add(item.path("listingId").asString()));
		return ids;
	}

	private static String codeOf(Result answer) {
		return answer.status() + " " + answer.body().path("code").asString();
	}

	private static String codeAndFields(Result answer) {
		return codeOf(answer) + " " + fields(answer);
	}

	private static String bodyWithoutCorrelation(Result answer) {
		return answer.body().path("code").asString() + "|" + answer.body().path("message").asString() + "|"
				+ answer.body().path("errors");
	}
}
