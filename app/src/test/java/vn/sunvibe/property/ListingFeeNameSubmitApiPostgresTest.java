package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * The fee names at submission over HTTP on PostgreSQL (listing-publish E06-S7 spec E06S7-D10, D11; criterion PB-11;
 * US-2 AC-4, E-20, LP-15): a fee name of the snapshot that breaks a machine rule refuses the submission with 422
 * {@code REVISION_NOT_SUBMITTABLE} and the entry {@code feeName.<position>.<RULE>}; the Draft stays saved and its read
 * lists the same problem with its position. A revision submitted with clean names stores {@code fee_names_checked =
 * true}, which the approval and the put live keep; its copy starts {@code false}. The fee name of E05's version in
 * effect is set by SQL after the agreement's chain and before the snapshot. The listing API tests' context. Needs Docker.
 */
class ListingFeeNameSubmitApiPostgresTest extends ListingPublicationApiTestBase {

	private static final String PHONE_FEE_NAME = "Gửi xe, gọi 0905 123 456";

	@Override
	String phonePrefix() {
		return "0983";
	}

	/** PB-11, E-20: 422 with the fee's position and rule; the Draft saved, its read listing the same problem. */
	@Test
	void submit_feeNameWithPhoneNumber_unprocessableDraftReadListsIt() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		assertEquals(1, jdbc.update("update brokerage_agreement_version_fees set name = ? where fee_no = 2 and"
				+ " version_id in (select id from brokerage_agreement_versions where agreement_id = ?)", PHONE_FEE_NAME,
				drafting.chain().agreementId()));
		completeDraft(drafting);

		Result answer = submit(sale, listingId, "1", dataVersion(listingId));

		assertEquals(422, answer.status(), answer.rawBody());
		assertEquals("REVISION_NOT_SUBMITTABLE", answer.body().path("code").asString());
		assertEquals(List.of("feeName.2.PHONE_NUMBER"), fields(answer));
		assertEquals("DRAFT", revisionStatus(listingId, 1));
		JsonNode read = getRevision(sale, listingId, "1").body();
		List<String> problems = new ArrayList<>();
		read.path("problems").forEach(problem -> problems.add(problem.path("part").asString() + " "
				+ problem.path("rule").asString() + " " + problem.path("position").asString()));
		assertEquals(List.of("FEE_NAME PHONE_NUMBER 2"), problems);
	}

	/**
	 * PB-11, E06S7-D10: the fee-name entries come after the description's, each fee alone in E05's order (a broken
	 * description and a broken fee name).
	 */
	@Test
	void submit_brokenDescriptionAndFeeName_feeEntryLast() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		jdbc.update("update brokerage_agreement_version_fees set name = ? where fee_no = 1 and version_id in"
				+ " (select id from brokerage_agreement_versions where agreement_id = ?)", "Phí dịch vụ đảm bảo",
				drafting.chain().agreementId());
		Result saved = saveContent(sale, listingId, "1", contentBody(dataVersion(listingId), TITLE,
				DESCRIPTION + " Xem thêm tại www.abc.vn", everyFeature()));
		assertEquals(200, saved.status(), saved.rawBody());
		Result captured = captureTerms(sale, listingId, "1", dataVersion(listingId), "IN_EFFECT");
		assertEquals(200, captured.status(), captured.rawBody());

		Result answer = submit(sale, listingId, "1", dataVersion(listingId));

		assertEquals(422, answer.status(), answer.rawBody());
		assertEquals(List.of("description.LINK", "feeName.1.FORBIDDEN_WORD"), fields(answer));
	}

	/**
	 * E06S7-D11: a revision submitted over HTTP with clean names ("Electricity", "Water") stores the flag true; the
	 * approval and the Sale's put live write the row again and keep it; the copy of the live revision starts false.
	 */
	@Test
	void submit_cleanFeeNames_flagTrueKeptByApprovalAndPutLiveCopyFalse() throws Exception {
		Drafting drafting = pendingDrafting();
		String listingId = drafting.listingId();
		assertEquals(Boolean.TRUE, feeNamesChecked(listingId, 1));

		unitAvailability(drafting.chain().unitId(), "OCCUPIED");
		Result recorded = recordFreshness(sale, listingId, validRecord(dataVersion(listingId)));
		assertEquals(201, recorded.status(), recorded.rawBody());
		decided(reviewer, listingId, "1", approval(dataVersion(listingId)));
		assertEquals("APPROVED", revisionStatus(listingId, 1));
		unitAvailability(drafting.chain().unitId(), "AVAILABLE");
		assertEquals(Boolean.TRUE, feeNamesChecked(listingId, 1));

		putLiveOk(listingId);
		assertEquals(Boolean.TRUE, feeNamesChecked(listingId, 1));

		Result copied = copy(sale, listingId, "1", dataVersion(listingId));
		assertEquals(201, copied.status(), copied.rawBody());
		assertEquals(Boolean.FALSE, feeNamesChecked(listingId, 2));
	}

	private Boolean feeNamesChecked(String listingId, int revisionNo) {
		return jdbc.queryForObject("select fee_names_checked from listing_revisions where listing_id = ? and"
				+ " revision_no = ?", Boolean.class, listingId, revisionNo);
	}
}
