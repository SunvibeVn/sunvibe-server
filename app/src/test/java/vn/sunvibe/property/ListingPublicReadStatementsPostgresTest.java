package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.listing.core.application.usecase.ListingPublicQueryUseCase;
import vn.sunvibe.listing.core.application.usecase.ListingPublicationQueryUseCase;

/**
 * The statements of the public read on PostgreSQL (listing-publish E06-S7 spec PB-21; ADR-012 decision 2: a number that
 * does not grow with the data; A-2): counted by {@link StatementCounter} around the use case alone on the test thread
 * (it counts the calling thread only, so not over HTTP). A visible listing with no fee and one with 20 fees, each
 * snapshot equal to E05's terms (the version's fee rows set by SQL before the submission over HTTP), approved while the
 * unit is occupied and put live by the Sale. Its imports and properties are those of
 * {@link ListingPutLiveStatementsPostgresTest}, so the classes share one context. Needs Docker.
 */
@Import(StatementCounter.Config.class)
class ListingPublicReadStatementsPostgresTest extends ListingPublicationApiTestBase {

	/** A-2: the count measured on the first green run (plan Task A8 Step 2, 2026-11-28), listing's reads and E05's. */
	private static final long MEASURED = 49;

	@Autowired
	private StatementCounter counter;

	/** listing-publish E06-S7 spec A-2, Task A9: the preview's count measured on its first green run (2026-11-28). */
	private static final long MEASURED_PREVIEW = 9;

	@Autowired
	private ListingPublicQueryUseCase publicReads;

	@Autowired
	private ListingPublicationQueryUseCase publications;

	@Override
	String phonePrefix() {
		return "0988";
	}

	/** PB-19, A-2: the preview of the live revision 1 of a listing without a fee runs the measured statements. */
	@Test
	void publicPreview_zeroFees_measuredCount() throws Exception {
		String listingId = listingOf(visibleWithFees(0));

		long statements = counter.count(() -> publications.publicPreview(listingId, 1, saleViewer()));

		assertEquals(MEASURED_PREVIEW, statements);
	}

	/** PB-19, A-2: the preview of a revision with 20 fees runs as many statements as one without a fee. */
	@Test
	void publicPreview_twentyFees_sameAsZeroFees() throws Exception {
		String none = listingOf(visibleWithFees(0));
		String twenty = listingOf(visibleWithFees(20));

		long withNone = counter.count(() -> publications.publicPreview(none, 1, saleViewer()));
		long withTwenty = counter.count(() -> publications.publicPreview(twenty, 1, saleViewer()));

		assertEquals(withNone, withTwenty);
	}

	private String listingOf(String code) {
		return jdbc.queryForObject("select id from listings where code = ?", String.class, code);
	}

	/** PB-21, A-2: the public read of a listing without a fee runs the measured number of statements. */
	@Test
	void publicListing_zeroFees_measuredCount() throws Exception {
		String code = visibleWithFees(0);

		long statements = counter.count(() -> publicReads.publicListing(code));

		assertEquals(MEASURED, statements);
	}

	/** PB-21: the public read of a listing with 20 fees runs as many statements as one without a fee. */
	@Test
	void publicListing_twentyFees_sameAsZeroFees() throws Exception {
		String none = visibleWithFees(0);
		String twenty = visibleWithFees(20);

		long withNone = counter.count(() -> publicReads.publicListing(none));
		long withTwenty = counter.count(() -> publicReads.publicListing(twenty));

		assertEquals(withNone, withTwenty);
	}

	/**
	 * A visible listing whose agreement's version holds that many fees (fee_no 1 to n, "Phí số n", AMOUNT 100000
	 * PER_MONTH, set by SQL): revision 1 submitted over HTTP, so its snapshot equals E05's terms; then the four steps of
	 * {@link #waitingApproval()} and the Sale's put live. Answers its code.
	 */
	private String visibleWithFees(int fees) throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		String versionId = jdbc.queryForObject("select id from brokerage_agreement_versions where agreement_id = ? and"
				+ " version_no = 1", String.class, drafting.chain().agreementId());
		jdbc.update("delete from brokerage_agreement_version_fees where version_id = ?", versionId);
		for (int feeNo = 1; feeNo <= fees; feeNo++) {
			jdbc.update("insert into brokerage_agreement_version_fees (version_id, fee_no, name, basis, amount,"
					+ " charge_unit) values (?, ?, ?, 'AMOUNT', 100000, 'PER_MONTH')", versionId, feeNo, "Phí số " + feeNo);
		}
		submitted(drafting);
		unitAvailability(drafting.chain().unitId(), "OCCUPIED");
		Result recorded = recordFreshness(sale, listingId, validRecord(dataVersion(listingId)));
		assertEquals(201, recorded.status(), recorded.rawBody());
		decided(reviewer, listingId, "1", approval(dataVersion(listingId)));
		assertEquals("APPROVED", revisionStatus(listingId, 1));
		unitAvailability(drafting.chain().unitId(), "AVAILABLE");
		putLiveOk(listingId);
		assertEquals(fees, count("select count(*) from listing_publication_fees where listing_id = ?", listingId));
		return jdbc.queryForObject("select code from listings where id = ?", String.class, listingId);
	}
}
