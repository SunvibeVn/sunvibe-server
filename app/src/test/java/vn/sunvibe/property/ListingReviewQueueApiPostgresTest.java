package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * The review queue over HTTP on PostgreSQL (listing-publish US-4 AC-1; E06-S4 spec 3.1, 4.1, E06S4-D2, E06S4-D10; RV-1,
 * RV-12): the Pending review revisions of listings not archived, oldest submission first, each with whether the caller
 * may review it and why not; a Draft, a withdrawn, a decided revision and one whose agreement ended are left out; the
 * four queue roles read it, a SALE and a CUSTOMER get 403; four full chunks of left-out rows answer no row and
 * {@code hasMore}. Needs Docker.
 */
class ListingReviewQueueApiPostgresTest extends ListingReviewApiTestBase {

	@Override
	String phonePrefix() {
		return "0908";
	}

	/**
	 * RV-1, RV-12: of this test's listings, the pending ones in submission order; the revisions are submitted out of
	 * their creation order, so the order by revision id (a ULID drawn at creation) differs from the order by submission.
	 */
	@Test
	void queue_pendingOldestFirst_draftCancelledDecidedEndedLeftOut() throws Exception {
		Drafting first = listingInEffect();
		Drafting second = listingInEffect();
		Drafting third = listingInEffect();
		Drafting draft = listingInEffect();
		Drafting withdrawn = pendingDrafting();
		Drafting decided = pendingDrafting();
		Drafting ended = pendingDrafting();
		submitted(third);
		submitted(first);
		submitted(second);
		assertEquals(200, withdraw(sale, withdrawn.listingId(), "1", dataVersion(withdrawn.listingId())).status());
		decided(reviewer, decided.listingId(), "1", returnBody(dataVersion(decided.listingId()), List.of("OTHER"), NOTE));
		Result termination = terminate(sale, ended.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		List<String> mine = List.of(first.listingId(), second.listingId(), third.listingId(), draft.listingId(),
				withdrawn.listingId(), decided.listingId(), ended.listingId());
		assertTrue(revisionId(first.listingId()).compareTo(revisionId(third.listingId())) < 0, "ids drawn in order");

		Result answer = queue(reviewer);

		assertEquals(200, answer.status(), answer.rawBody());
		List<String> listed = new ArrayList<>();
		answer.body().path("items").forEach(item -> {
			if (mine.contains(item.path("listingId").asString())) {
				listed.add(item.path("listingId").asString());
			}
		});
		assertEquals(List.of(third.listingId(), first.listingId(), second.listingId()), listed);
		assertFalse(answer.body().path("hasMore").asBoolean(), answer.rawBody());
	}

	/** RV-1: the reviewer's row is reviewable; the same row for a manager NOT_A_REVIEWER, for an author AUTHOR. */
	@Test
	void queue_rowsSayEligibilityAndReasons() throws Exception {
		TestUser author = survey.user("Queue Author Reviewer", "SALE", "LISTING_REVIEWER");
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), author);
		String listingId = newListing(author, chain.agreementId());
		submittedBy(author, listingId);
		fixture.checklists().reassign(leader, chain.leadId(), sale);

		Map<String, String> rows = new LinkedHashMap<>();
		for (TestUser caller : List.of(reviewer, manager, author)) {
			JsonNode row = row(queue(caller), listingId);
			rows.put(caller.userId(), row.path("viewerMayReview").asBoolean() + " " + row.path("notEligibleReasons"));
		}

		assertEquals(List.of("true []", "false [\"NOT_A_REVIEWER\"]", "false [\"AUTHOR\"]"), List.copyOf(rows.values()));
	}

	/** E06S4-D2: the four queue roles read it; a SALE and a CUSTOMER get 403 FORBIDDEN. */
	@Test
	void queue_queueRoles_okSaleAndCustomerForbidden() throws Exception {
		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(reviewer, leader, manager, admin, sale, customer)) {
			Result answer = queue(caller);
			answers.add(answer.status() == 200 ? "200" : codeOf(answer));
		}

		assertEquals(List.of("200", "200", "200", "200", "403 FORBIDDEN", "403 FORBIDDEN"), answers);
	}

	/** RV-1: a row's code, revision, submission time, title and the unit snapshot of the submission; no user id. */
	@Test
	void queue_rowFields_codeTitleUnitNoUserId() throws Exception {
		String listingId = pending();
		JsonNode revision = getRevision(sale, listingId, "1").body();

		JsonNode row = row(queue(reviewer), listingId);

		List<String> names = new ArrayList<>();
		row.properties().forEach(property -> names.add(property.getKey()));
		assertEquals(List.of("code", "listingId", "notEligibleReasons", "revisionNo", "submittedAt", "title", "unit",
				"viewerMayReview"), names.stream().sorted().toList());
		assertTrue(row.path("code").asString().matches(CODE_FORM), row.toString());
		assertEquals(jdbc.queryForObject("select code from listings where id = ?", String.class, listingId),
				row.path("code").asString());
		assertEquals(1, row.path("revisionNo").asInt());
		assertEquals(Instant.parse(revision.path("submittedAt").asString()), Instant.parse(row.path("submittedAt")
				.asString()));
		assertEquals(TITLE, row.path("title").asString());
		assertEquals(revision.path("unitSnapshot"), row.path("unit"));
		assertFalse(row.toString().contains(sale.userId()), row.toString());
	}

	/**
	 * E06S4-P9, RV-1: 1,000 Pending review revisions older than any other, of listings whose agreement E05 does not
	 * know, are read as ended and left out; four full chunks: no row of them answered, {@code hasMore} true.
	 */
	@Test
	void queue_fourFullChunksLeftOut_noRowsHasMore() throws Exception {
		String template = pending();
		try {
			copies(template, 1_000);
			assertEquals(1_000, count("select count(*) from listing_revisions where id like '01JQUEUERV%'"
					+ " and status = 'PENDING_REVIEW'"));

			Result answer = queue(reviewer);

			assertEquals(200, answer.status(), answer.rawBody());
			List<String> copied = new ArrayList<>();
			answer.body().path("items").forEach(item -> {
				if (item.path("listingId").asString().startsWith("01JQUEUELS")) {
					copied.add(item.path("listingId").asString());
				}
			});
			assertEquals(List.of(), copied);
			assertTrue(answer.body().path("hasMore").asBoolean(), answer.rawBody());
		} finally {
			jdbc.update("delete from listing_revisions where id like '01JQUEUERV%'");
			jdbc.update("delete from listings where id like '01JQUEUELS%'");
		}
	}

	/** The queue row of the listing, which must be listed. */
	private static JsonNode row(Result answer, String listingId) {
		assertEquals(200, answer.status(), answer.rawBody());
		for (JsonNode item : answer.body().path("items")) {
			if (listingId.equals(item.path("listingId").asString())) {
				return item;
			}
		}
		throw new AssertionError("not listed: " + listingId + " " + answer.rawBody());
	}

	private String revisionId(String listingId) {
		return jdbc.queryForObject("select id from listing_revisions where listing_id = ? and revision_no = 1",
				String.class, listingId);
	}

	/** Revision 1 completed and submitted by the caller, its assigned Sale. */
	private void submittedBy(TestUser caller, String listingId) throws Exception {
		assertEquals(200, saveContent(caller, listingId, "1", completeContentBody(dataVersion(listingId))).status());
		assertEquals(200, captureTerms(caller, listingId, "1", dataVersion(listingId), "IN_EFFECT").status());
		Result answer = submit(caller, listingId, "1", dataVersion(listingId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	/**
	 * Copies of the template's listing row and Pending review revision in one SQL batch each: listing ids
	 * 01JQUEUELS..., agreement ids E05 does not know, codes TD-ZZZY-xxxx, revisions submitted a day before the template.
	 */
	private void copies(String templateListingId, int copies) {
		Map<String, String> listing = Map.of(
				"id", "'01JQUEUELS' || lpad(g::text, 16, '0')",
				"code", "'TD-ZZZY-' || chr(50 + (g / 512) % 8) || chr(50 + (g / 64) % 8) || chr(50 + (g / 8) % 8)"
						+ " || chr(50 + g % 8)",
				"agreement_id", "'01JQUEUEAG' || lpad(g::text, 16, '0')");
		Map<String, String> revision = Map.of(
				"id", "'01JQUEUERV' || lpad(g::text, 16, '0')",
				"listing_id", "'01JQUEUELS' || lpad(g::text, 16, '0')",
				"submitted_at", "t.submitted_at - interval '1 day'");
		jdbc.update(copySql("listings", listing) + " where t.id = ?", copies, templateListingId);
		jdbc.update(copySql("listing_revisions", revision) + " where t.listing_id = ? and t.revision_no = 1", copies,
				templateListingId);
	}

	private String copySql(String table, Map<String, String> replaced) {
		List<String> columns = jdbc.queryForList("select column_name from information_schema.columns where table_name = ?"
				+ " and table_schema = current_schema() order by ordinal_position", String.class, table);
		List<String> values = columns.stream().map(column -> replaced.getOrDefault(column, "t." + column)).toList();
		return "insert into " + table + " (" + String.join(", ", columns) + ") select " + String.join(", ", values)
				+ " from " + table + " t, generate_series(1, ?) g";
	}
}
