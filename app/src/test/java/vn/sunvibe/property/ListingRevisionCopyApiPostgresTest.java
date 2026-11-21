package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * The copy of a returned revision over HTTP on PostgreSQL (listing-publish US-2 AC-6, BR-6; E06-S4 spec 3.6, 4.6,
 * E06S4-D14; RV-10, RV-6, RV-11, RV-12): the assigned Sale copies a Needs changes revision into a new Draft with its
 * content, terms, fee rows and source number; any other source, an open revision, a stale data version and an ended
 * agreement are refused with nothing written; a broken body answers 400 before the role. Needs Docker.
 */
class ListingRevisionCopyApiPostgresTest extends ListingReviewApiTestBase {

	@Override
	String phonePrefix() {
		return "0903";
	}

	/** RV-10: 201 with n + 1; title, description, features, terms and fee rows equal the source's; the source set. */
	@Test
	void copy_needsChanges_draftWithContentTermsFeesAndSource() throws Exception {
		String listingId = returned();
		long version = dataVersion(listingId);

		Result answer = copy(sale, listingId, "1", version);

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(List.of(listingId, "2", "DRAFT", String.valueOf(version + 1)),
				List.of(answer.body().path("listingId").asString(), answer.body().path("revisionNo").asString(),
						answer.body().path("status").asString(), answer.body().path("dataVersion").asString()));
		JsonNode source = getRevision(sale, listingId, "1").body();
		JsonNode copied = getRevision(sale, listingId, "2").body();
		for (String part : List.of("title", "description", "features")) {
			assertEquals(source.path(part), copied.path(part), part);
		}
		assertEquals(source.path("termsSnapshot").path("terms"), copied.path("termsSnapshot").path("terms"));
		assertEquals(source.path("termsSnapshot").path("source"), copied.path("termsSnapshot").path("source"));
		assertTrue(copied.path("unitSnapshot").isNull(), copied.toString());
		assertTrue(copied.path("submittedAt").isNull(), copied.toString());
		assertEquals(List.of(1, sale.userId()), List.of(jdbc.queryForObject("select copied_from_revision_no from"
				+ " listing_revisions where listing_id = ? and revision_no = 2", Integer.class, listingId),
				jdbc.queryForObject("select created_by from listing_revisions where listing_id = ? and revision_no = 2",
						String.class, listingId)));
		assertEquals(fees(listingId, 1), fees(listingId, 2));
		assertTrue(fees(listingId, 1) > 0);
		assertEquals("REVISION_COPIED 2 " + sale.userId(), history(listingId).getLast());
	}

	/** RV-10, RV-6: a rejected, a withdrawn and an approved revision answer REVISION_NOT_COPYABLE; nothing written. */
	@Test
	void copy_rejectedCancelledOrOther_notCopyableNothingWritten() throws Exception {
		String rejected = pending();
		decided(reviewer, rejected, "1", rejection(dataVersion(rejected), "WRONG_UNIT", NOTE));
		String withdrawn = pending();
		assertEquals(200, withdraw(sale, withdrawn, "1", dataVersion(withdrawn)).status());
		String approved = pending();
		decided(reviewer, approved, "1", approval(dataVersion(approved)));
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (String listingId : List.of(rejected, withdrawn, approved)) {
			answers.add(codeOf(copy(sale, listingId, "1", dataVersion(listingId))));
		}

		assertEquals(List.of("409 REVISION_NOT_COPYABLE", "409 REVISION_NOT_COPYABLE", "409 REVISION_NOT_COPYABLE"),
				answers);
		assertEquals(before, written());
	}

	/** RV-10, BR-6: a Draft open beside the returned revision: OPEN_REVISION_EXISTS. */
	@Test
	void copy_openRevision_openRevisionExists() throws Exception {
		String listingId = returned();
		assertEquals(201, startRevision(sale, listingId, dataVersion(listingId)).status());
		Written before = written();

		Result answer = copy(sale, listingId, "1", dataVersion(listingId));

		assertCode(409, "OPEN_REVISION_EXISTS", answer);
		assertEquals(before, written());
	}

	/** RV-11, listing BR-3: a stale data version answers CONCURRENT_MODIFICATION; nothing written. */
	@Test
	void copy_staleDataVersion_concurrentModification() throws Exception {
		String listingId = returned();
		Written before = written();

		Result answer = copy(sale, listingId, "1", dataVersion(listingId) - 1);

		assertCode(409, "CONCURRENT_MODIFICATION", answer);
		assertEquals(before, written());
	}

	/** RV-6: no data version, and a body that is not JSON, answer 400 before the role, to the Sale and a manager. */
	@Test
	void copy_brokenBody_badRequestBeforeRole() throws Exception {
		Written before = written();

		List<String> answers = new ArrayList<>();
		for (TestUser caller : List.of(sale, manager)) {
			answers.add(codeAndFields(copy(caller, UNKNOWN, "1", null)));
			answers.add(codeAndFields(postRaw(caller, LISTINGS + "/" + UNKNOWN + "/revisions/1/copies", "copy")));
		}

		assertEquals(List.of("400 VALIDATION_FAILED [dataVersion]", "400 MALFORMED_REQUEST []",
				"400 VALIDATION_FAILED [dataVersion]", "400 MALFORMED_REQUEST []"), answers);
		assertEquals(before, written());
	}

	/** RV-12: a returned revision of an ended agreement answers LISTING_ARCHIVED; nothing written. */
	@Test
	void copy_endedAgreement_listingArchived() throws Exception {
		Drafting drafting = pendingDrafting();
		String listingId = drafting.listingId();
		decided(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("OTHER"), NOTE));
		Result termination = terminate(sale, drafting.chain().agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		Written before = written();

		Result answer = copy(sale, listingId, "1", dataVersion(listingId));

		assertCode(409, "LISTING_ARCHIVED", answer);
		assertEquals(before, written());
	}

	/** A pending listing whose revision 1 a reviewer returned. */
	private String returned() throws Exception {
		String listingId = pending();
		decided(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("TEXT_RULE_VIOLATION"), NOTE));
		return listingId;
	}

	private int fees(String listingId, int revisionNo) {
		return count("select count(*) from listing_revision_fees f join listing_revisions r on r.id = f.revision_id"
				+ " where r.listing_id = ? and r.revision_no = ?", listingId, revisionNo);
	}
}
