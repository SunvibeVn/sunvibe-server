package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.listing.core.application.usecase.ListingCommandUseCase;

/**
 * What the listing API tests of listing-publish E06-S1 share (plan decision E06S1-P5): the agreements of
 * {@link AgreementStopApiTestBase} (unchanged, with its context, its chain over HTTP and its held locks, E06S1-D13), the
 * three listing calls over HTTP, a LISTING_REVIEWER and a CUSTOMER, the count of the listing tables and of the outbox,
 * and the cleanup of the listing rows of every agreement a test created. Needs Docker.
 */
abstract class ListingApiTestBase extends AgreementStopApiTestBase {

	static final String LISTINGS = "/api/v1/staff/listings";
	static final String CODE_FORM = "^TD-[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$";
	/** A well-formed id no agreement or listing has. */
	static final String UNKNOWN = "01JFUNKNOWN000000000000000";

	@Autowired
	ListingCommandUseCase listingCommands;

	TestUser reviewer;
	TestUser customer;

	@BeforeAll
	void setUpListingUsers() throws Exception {
		reviewer = survey.user("Listing Reviewer", "LISTING_REVIEWER");
		customer = survey.user("Listing Customer", "CUSTOMER");
	}

	/** The listing rows of every agreement this test created, children first; runs before the agreements go. */
	@AfterEach
	void deleteListingRows() {
		for (String agreementId : created) {
			jdbc.update("delete from listing_history where listing_id in (select id from listings where agreement_id = ?)",
					agreementId);
			jdbc.update("delete from listing_revisions where listing_id in (select id from listings where agreement_id = ?)",
					agreementId);
			jdbc.update("delete from listings where agreement_id = ?", agreementId);
		}
	}

	// ==================== the three listing calls over HTTP ====================

	Result createListing(TestUser caller, Object agreementId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("agreementId", agreementId);
		return api.post(caller, LISTINGS, body);
	}

	Result getListing(TestUser caller, String listingId) throws Exception {
		return api.get(caller, LISTINGS + "/" + listingId);
	}

	Result getAgreementListings(TestUser caller, String agreementId) throws Exception {
		return api.get(caller, LISTINGS + "/by-agreement/" + agreementId);
	}

	/** The create by the caller, which must answer 201; the new listing's id. */
	String newListing(TestUser caller, String agreementId) throws Exception {
		Result answer = createListing(caller, agreementId);
		assertEquals(201, answer.status(), answer.rawBody());
		return answer.body().path("listingId").asString();
	}

	/** An owner stop beside the agreement: a co-owner of its property, the OWNER group of E05's answer. */
	void coOwnerOf(Chain chain) {
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state,"
				+ " valid_from, valid_to, created_at) values (?, ?, ?, 'CO_OWNER', 'UNVERIFIED', ?, null, ?)",
				ids.generateId(), chain.propertyId(), ids.generateId(), java.time.LocalDate.of(2026, 1, 1),
				java.sql.Timestamp.from(Instant.now()));
	}

	// ==================== nothing written ====================

	/** The rows of the three listing tables and of the outbox: what a refused or read-only call leaves as it is. */
	record ListingRows(int listings, int revisions, int history, int outbox) {
	}

	ListingRows listingRows() {
		return new ListingRows(count("select count(*) from listings"), count("select count(*) from listing_revisions"),
				count("select count(*) from listing_history"), outboxCount());
	}

	void assertNoListingWritten(ListingRows before) {
		assertEquals(before, listingRows());
	}

	int outboxCount() {
		return count("select count(*) from outbox_events");
	}
}
