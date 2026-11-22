package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;

/**
 * Pin P-9 of listing-publish E06-S6 (spec section 8, S-13, U-5): every reader of the stored listing state answers a
 * {@code PUBLISHED} listing as its code answers it today, before any command can store that state. The state is written
 * by SQL after the fixture each read needs: the listing read, the agreement's listing section, the create beside it, the
 * review queue, the reconfirmation list and a content save. Needs Docker.
 */
class ListingPublishedStateApiPostgresTest extends ListingReviewApiTestBase {

	@Override
	String phonePrefix() {
		return "0972";
	}

	/** The listing read answers the stored state. */
	@Test
	void getListing_publishedBySql_answersPublished() throws Exception {
		String listingId = listingInEffect().listingId();
		published(listingId);

		Result read = getListing(sale, listingId);

		assertEquals(200, read.status(), read.rawBody());
		assertEquals("PUBLISHED", read.body().path("state").asString());
	}

	/** A Published listing is the agreement's open listing: listed with its state, and no second one may be created. */
	@Test
	void agreementListings_published_openLineNoCreate() throws Exception {
		Drafting drafting = listingInEffect();
		published(drafting.listingId());

		Result read = getAgreementListings(sale, drafting.chain().agreementId());

		assertEquals(200, read.status(), read.rawBody());
		JsonNode listings = read.body().path("listings");
		assertEquals(1, listings.size(), listings.toString());
		assertEquals(drafting.listingId(), listings.path(0).path("listingId").asString());
		assertEquals("PUBLISHED", listings.path(0).path("state").asString());
		assertFalse(read.body().path("creation").path("viewerMayCreate").asBoolean(), read.rawBody());
	}

	/** listing BR-1: a create beside a Published listing of the agreement answers 409 LISTING_EXISTS. */
	@Test
	void create_besidePublished_listingExists() throws Exception {
		Drafting drafting = listingInEffect();
		published(drafting.listingId());

		Result answer = createListing(sale, drafting.chain().agreementId());

		assertCode(409, "LISTING_EXISTS", answer);
	}

	/** The review queue lists the Pending review revision of a Published listing. */
	@Test
	void reviewQueue_publishedListing_pendingRevisionListed() throws Exception {
		String listingId = pending();
		published(listingId);

		Result read = queue(reviewer);

		assertEquals(200, read.status(), read.rawBody());
		List<String> rows = new ArrayList<>();
		read.body().path("items").forEach(item -> {
			if (listingId.equals(item.path("listingId").asString())) {
				rows.add(item.path("revisionNo").asInt() + "");
			}
		});
		assertEquals(List.of("1"), rows);
	}

	/**
	 * The reconfirmation list keeps a Published listing whose valid confirmation is 12 days back (due from 10 days, and
	 * away from the day boundary of E06S3-D14), with its state.
	 */
	@Test
	void reconfirmations_publishedListing_listedWithItsState() throws Exception {
		String listingId = listingInEffect().listingId();
		LocalDate confirmedOn = today().minusDays(12);
		jdbc.update("update listings set freshness_confirmed_on = ?, freshness_latest_valid = true where id = ?",
				Date.valueOf(confirmedOn), listingId);
		published(listingId);

		Result read = api.get(sale, LISTINGS + "/reconfirmations");

		assertEquals(200, read.status(), read.rawBody());
		List<String> rows = new ArrayList<>();
		read.body().path("items").forEach(item -> {
			if (listingId.equals(item.path("listingId").asString())) {
				rows.add(item.path("state").asString() + " " + item.path("confirmedOn").asString());
			}
		});
		assertEquals(List.of("PUBLISHED " + confirmedOn), rows);
	}

	/** A content save of the Draft revision 1 of a Published listing answers 200. */
	@Test
	void saveContent_publishedListing_ok() throws Exception {
		String listingId = listingInEffect().listingId();
		published(listingId);

		Result saved = saveContent(sale, listingId, "1", completeContentBody(dataVersion(listingId)));

		assertEquals(200, saved.status(), saved.rawBody());
	}

	/** The listing stored Published by SQL, its row version kept. */
	private void published(String listingId) {
		assertEquals(1, jdbc.update("update listings set state = 'PUBLISHED' where id = ?", listingId));
	}
}
