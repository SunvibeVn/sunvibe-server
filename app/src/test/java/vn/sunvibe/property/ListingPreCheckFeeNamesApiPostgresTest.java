package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfileFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitProfilePort;

/**
 * A revision submitted before the fee-name check went live (listing-publish E06-S7 spec E06S7-D4, D11; criterion PB-10;
 * BR-2): its flag {@code fee_names_checked} is false, so its put live checks the fee names once. The fixture: the
 * agreement's version in effect has its second fee renamed by SQL before the Sale takes the snapshot over HTTP (the
 * snapshot then equals E05's terms); the content saved over HTTP; the submission written by SQL as the submission wrote
 * it before the check, the unit snapshot from property's profile port; a valid confirmation over HTTP; the approval
 * over HTTP, which puts the revision live. A name that fails the check gets no publication row and the code answers
 * 404; one that passes gets its row and answers 200. A listing stored PUBLISHED with a LIVE revision but no row (put
 * live before the deploy) answers 404. The flag of a revision submitted over HTTP and of its copy is held by
 * {@code ListingFeeNameSubmitApiPostgresTest}. Needs Docker.
 */
class ListingPreCheckFeeNamesApiPostgresTest extends ListingPublicReadApiTestBase {

	/** listing-publish example 14: a phone number in a fee name. */
	private static final String UNCHECKED_NAME = "Gửi xe, gọi 0905 123 456";
	private static final String PASSING_NAME = "Gửi xe máy";

	@Autowired
	UnitProfilePort unitProfiles;

	@Override
	String phonePrefix() {
		return "0986";
	}

	/** PB-10: the unchecked name that fails the check: LIVE and PUBLISHED, no publication row, 404. */
	@Test
	void publicRead_preCheckRevisionFailingName_liveWithoutRowNotFound() throws Exception {
		String listingId = approvedBeforeTheCheck(UNCHECKED_NAME);

		assertEquals(List.of("LIVE", "PUBLISHED", "rows 0", "404 NOT_FOUND"), List.of(revisionStatus(listingId, 1),
				state(listingId), "rows " + publicationRows(listingId), codeOf(publicRead(publicCodeOf(listingId)))));
	}

	/** PB-10: the same fixture with a name that passes: the row is written and the code answers 200. */
	@Test
	void publicRead_preCheckRevisionPassingName_rowWrittenShown() throws Exception {
		String listingId = approvedBeforeTheCheck(PASSING_NAME);

		Result answer = publicRead(publicCodeOf(listingId));
		assertEquals(List.of("LIVE", "PUBLISHED", "rows 1", "200 " + PASSING_NAME), List.of(revisionStatus(listingId, 1),
				state(listingId), "rows " + publicationRows(listingId), answer.status() + " "
						+ answer.body().path("terms").path("mandatoryFees").path(1).path("name").asString()));
	}

	/** PB-10, E06S7-D4: a listing stored PUBLISHED with a LIVE revision and no row, as one put live before the deploy. */
	@Test
	void publicRead_publishedWithoutRow_notFound() throws Exception {
		String listingId = liveListing().listingId();
		String code = publicCodeOf(listingId);
		assertEquals(200, publicRead(code).status());

		assertEquals(1, jdbc.update("delete from listing_publications where listing_id = ?", listingId));

		assertEquals(List.of("LIVE", "PUBLISHED"), List.of(revisionStatus(listingId, 1), state(listingId)));
		assertEquals("404 NOT_FOUND", codeOf(publicRead(code)));
	}

	/**
	 * The fixture of PB-10: the second fee of the version in effect named by SQL; the content and the snapshot over
	 * HTTP; the submission by SQL with the flag false; a valid confirmation and the approval over HTTP.
	 */
	private String approvedBeforeTheCheck(String secondFeeName) throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		assertEquals(1, jdbc.update("update brokerage_agreement_version_fees set name = ? where fee_no = 2 and"
				+ " version_id = (select id from brokerage_agreement_versions where agreement_id = ? and version_no = 1)",
				secondFeeName, drafting.chain().agreementId()));
		completeDraft(drafting);
		UnitProfileFacts unit = unitProfiles.profile(drafting.chain().unitId()).orElseThrow();
		assertEquals(1, jdbc.update("update listing_revisions set status = 'PENDING_REVIEW', unit_asset_type = ?,"
				+ " unit_province_code = ?, unit_province_name = ?, unit_ward_code = ?, unit_ward_name = ?,"
				+ " unit_bedrooms = ?, unit_bathrooms = ?, unit_area_sqm = ?, submitted_at = ?, submitted_by = ?,"
				+ " fee_names_checked = false where listing_id = ? and revision_no = 1", unit.assetTypeOrNull(),
				unit.provinceCodeOrNull(), unit.provinceNameOrNull(), unit.wardCodeOrNull(), unit.wardNameOrNull(),
				unit.bedrooms(), unit.bathrooms(), unit.areaSqm(),
				Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)), sale.userId(), listingId));
		Result recorded = recordFreshness(sale, listingId, validRecord(dataVersion(listingId)));
		assertEquals(201, recorded.status(), recorded.rawBody());
		assertEquals(secondFeeName, jdbc.queryForObject("select f.name from listing_revision_fees f join"
				+ " listing_revisions r on r.id = f.revision_id where r.listing_id = ? and f.fee_no = 2", String.class,
				listingId));

		decided(reviewer, listingId, "1", approval(dataVersion(listingId)));

		return listingId;
	}
}
