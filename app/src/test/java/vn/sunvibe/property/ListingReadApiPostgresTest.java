package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * The listing read and the agreement's listing section over HTTP on PostgreSQL (listing BR-5, BR-16; listing-publish
 * US-1 AC-3, AC-5, E-3, E-11; E06-S1 spec 3.3, 3.4, section 4, E06S1-D5, E06S1-D8; LC-8 to LC-11): the role table, the
 * IDOR cases with one 404 per caller, the rights moving at a reassignment, the creation part and the archived state of
 * an ended agreement. Reads write nothing. Needs Docker.
 */
class ListingReadApiPostgresTest extends ListingApiTestBase {

	private static final List<String> DETAIL_FIELDS = List.of("agreementId", "code", "createdAt", "dataVersion",
			"hasOlderRevisions", "listingId", "openRevisionNo", "revisions", "state", "transactionType", "unitId",
			"viewerIsAssignedSale");

	@Override
	String phonePrefix() {
		return "0919";
	}

	/** LC-8: managers, reviewers and the assignee read the listing, with the fields of section 4; a CUSTOMER gets 403. */
	@Test
	void getListing_roles_asTheTable() throws Exception {
		Chain chain = inEffect();
		String listingId = newListing(sale, chain.agreementId());
		ListingRows before = listingRows();

		for (TestUser caller : List.of(leader, manager, admin, reviewer, sale)) {
			Result answer = getListing(caller, listingId);
			assertEquals(200, answer.status(), answer.rawBody());
			JsonNode body = answer.body();
			assertEquals(DETAIL_FIELDS, names(body), answer.rawBody());
			assertEquals(listingId, body.path("listingId").asString());
			assertTrue(body.path("code").asString().matches(CODE_FORM), answer.rawBody());
			assertEquals("UNPUBLISHED", body.path("state").asString());
			assertEquals("LONG_TERM_RENT", body.path("transactionType").asString());
			assertEquals(chain.unitId(), body.path("unitId").asString());
			assertEquals(chain.agreementId(), body.path("agreementId").asString());
			assertFalse(body.path("createdAt").asString().isEmpty(), answer.rawBody());
			assertEquals(0, body.path("dataVersion").asLong());
			assertEquals(caller == sale, body.path("viewerIsAssignedSale").asBoolean(), answer.rawBody());
			assertEquals(1, body.path("revisions").size(), answer.rawBody());
			assertEquals(1, body.path("openRevisionNo").asInt(), answer.rawBody());
			assertEquals(1, body.path("revisions").path(0).path("revisionNo").asInt());
			assertEquals("DRAFT", body.path("revisions").path(0).path("status").asString());
			assertFalse(body.path("hasOlderRevisions").asBoolean());
		}
		assertCode(403, "FORBIDDEN", getListing(customer, listingId));
		assertNoListingWritten(before);
	}

	/** LC-8: the four roles of E05's agreement page read the section; a CUSTOMER gets 403. */
	@Test
	void getAgreementListings_roles_asTheTable() throws Exception {
		Chain chain = inEffect();
		newListing(sale, chain.agreementId());
		ListingRows before = listingRows();

		for (TestUser caller : List.of(leader, manager, admin, sale)) {
			Result answer = getAgreementListings(caller, chain.agreementId());
			assertEquals(200, answer.status(), answer.rawBody());
			assertEquals(chain.agreementId(), answer.body().path("agreementId").asString());
			assertEquals(1, answer.body().path("listings").size(), answer.rawBody());
		}
		assertCode(403, "FORBIDDEN", getAgreementListings(customer, chain.agreementId()));
		assertNoListingWritten(before);
	}

	/** LC-8, E06S1-D8: LISTING_REVIEWER alone gets 403 on the section, with or without a listing, and learns nothing. */
	@Test
	void getAgreementListings_reviewerOnly_forbiddenWithOrWithoutListing() throws Exception {
		Chain withListing = inEffect();
		newListing(sale, withListing.agreementId());
		Chain withoutListing = inEffect();
		ListingRows before = listingRows();

		for (Chain chain : List.of(withListing, withoutListing)) {
			Result answer = getAgreementListings(reviewer, chain.agreementId());
			assertCode(403, "FORBIDDEN", answer);
			for (String hidden : List.of("reasonGroups", "advertisingNotStarted", "viewerMayCreate", chain.agreementId())) {
				assertFalse(answer.rawBody().contains(hidden), hidden + " " + answer.rawBody());
			}
		}
		assertNoListingWritten(before);
	}

	/** LC-8, listing BR-5: another SALE, a malformed id and an unknown id get one 404 body on both paths. */
	@Test
	void reads_otherSaleMalformedOrUnknown_sameNotFound() throws Exception {
		Chain chain = inEffect();
		String listingId = newListing(sale, chain.agreementId());
		ListingRows before = listingRows();

		List<Result> answers = new ArrayList<>();
		answers.add(getListing(otherSale, listingId));
		answers.add(getAgreementListings(otherSale, chain.agreementId()));
		answers.add(getListing(sale, listingId.toLowerCase(Locale.ROOT)));
		answers.add(getAgreementListings(sale, chain.agreementId().toLowerCase(Locale.ROOT)));
		answers.add(getListing(sale, listingId.substring(1)));
		answers.add(getAgreementListings(sale, chain.agreementId().substring(1)));
		answers.add(getListing(sale, UNKNOWN));
		answers.add(getAgreementListings(sale, UNKNOWN));

		for (Result answer : answers) {
			assertCode(404, "NOT_FOUND", answer);
			assertEquals(bodyWithoutCorrelation(answers.getFirst()), bodyWithoutCorrelation(answer), answer.rawBody());
		}
		assertNoListingWritten(before);
	}

	/** LC-9, listing BR-16: a reassignment between two SALEs moves the rights at once; the rows keep their authors. */
	@Test
	void reads_reassignedBasisLead_rightsMoveAtOnce() throws Exception {
		Chain chain = inEffect();
		String listingId = newListing(sale, chain.agreementId());
		Map<String, Object> revision = jdbc.queryForMap("select status, created_by, created_at, updated_at from"
				+ " listing_revisions where listing_id = ?", listingId);
		fixture.checklists().reassign(leader, chain.leadId(), otherSale);
		ListingRows before = listingRows();

		assertCode(404, "NOT_FOUND", getListing(sale, listingId));
		assertCode(404, "NOT_FOUND", getAgreementListings(sale, chain.agreementId()));
		assertCode(404, "NOT_FOUND", createListing(sale, chain.agreementId()));
		Result read = getListing(otherSale, listingId);
		assertEquals(200, read.status(), read.rawBody());
		assertTrue(read.body().path("viewerIsAssignedSale").asBoolean(), read.rawBody());
		Result section = getAgreementListings(otherSale, chain.agreementId());
		assertEquals(200, section.status(), section.rawBody());
		assertEquals(revision, jdbc.queryForMap("select status, created_by, created_at, updated_at from"
				+ " listing_revisions where listing_id = ?", listingId));
		assertEquals(sale.userId(), revision.get("created_by"));
		assertEquals(List.of(sale.userId()), jdbc.queryForList("select actor_user_id from listing_history where"
				+ " listing_id = ?", String.class, listingId));
		assertCode(409, "LISTING_EXISTS", createListing(otherSale, chain.agreementId()));
		assertNoListingWritten(before);
	}

	/** LC-9: a former assignee holding SALE_LEADER gets 404 on the create and still reads both, not as the assignee. */
	@Test
	void reads_formerAssigneeLeader_readsWithoutAssignedFlag() throws Exception {
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), leader);
		String listingId = newListing(leader, chain.agreementId());
		fixture.checklists().reassign(leader, chain.leadId(), sale);
		ListingRows before = listingRows();

		assertCode(404, "NOT_FOUND", createListing(leader, chain.agreementId()));
		Result read = getListing(leader, listingId);
		assertEquals(200, read.status(), read.rawBody());
		assertFalse(read.body().path("viewerIsAssignedSale").asBoolean(), read.rawBody());
		Result section = getAgreementListings(leader, chain.agreementId());
		assertEquals(200, section.status(), section.rawBody());
		assertFalse(section.body().path("creation").path("viewerMayCreate").asBoolean(), section.rawBody());
		assertNoListingWritten(before);
	}

	/** LC-10: the assignee may create before the create and not after it; a manager never. */
	@Test
	void getAgreementListings_creation_beforeAndAfterTheCreate() throws Exception {
		Chain chain = inEffect();

		Result before = getAgreementListings(sale, chain.agreementId());
		assertEquals(200, before.status(), before.rawBody());
		assertTrue(before.body().path("creation").path("viewerMayCreate").asBoolean(), before.rawBody());
		assertFalse(before.body().path("creation").path("advertisingNotStarted").asBoolean(), before.rawBody());
		assertEquals(0, before.body().path("creation").path("reasonGroups").size(), before.rawBody());
		assertEquals(0, before.body().path("listings").size(), before.rawBody());
		assertFalse(before.body().path("hasOlder").asBoolean());
		Result managers = getAgreementListings(manager, chain.agreementId());
		assertFalse(managers.body().path("creation").path("viewerMayCreate").asBoolean(), managers.rawBody());

		String listingId = newListing(sale, chain.agreementId());
		Result after = getAgreementListings(sale, chain.agreementId());

		assertEquals(200, after.status(), after.rawBody());
		JsonNode item = after.body().path("listings").path(0);
		assertEquals(1, after.body().path("listings").size(), after.rawBody());
		assertEquals(listingId, item.path("listingId").asString());
		assertEquals("UNPUBLISHED", item.path("state").asString());
		assertFalse(after.body().path("creation").path("viewerMayCreate").asBoolean(), after.rawBody());
	}

	/** LC-10, listing-publish LP-1: NOT_STARTED alone: advertisingNotStarted, no group, and the create offered. */
	@Test
	void getAgreementListings_notStartedVersion_advertisingNotStarted() throws Exception {
		Chain chain = activated(today().plusDays(10), today().plusMonths(6));
		ListingRows before = listingRows();

		Result answer = getAgreementListings(sale, chain.agreementId());

		assertEquals(200, answer.status(), answer.rawBody());
		JsonNode creation = answer.body().path("creation");
		assertTrue(creation.path("advertisingNotStarted").asBoolean(), answer.rawBody());
		assertEquals(0, creation.path("reasonGroups").size(), answer.rawBody());
		assertTrue(creation.path("viewerMayCreate").asBoolean(), answer.rawBody());
		assertNoListingWritten(before);
	}

	/** LC-10, ADR-012 decision 2: once the agreement ended, both reads answer ARCHIVED and the group ENDED. */
	@Test
	void reads_endedAgreement_archivedAndEndedGroup() throws Exception {
		Chain chain = inEffect();
		String listingId = newListing(sale, chain.agreementId());
		Result termination = terminate(sale, chain.agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		ListingRows before = listingRows();

		Result read = getListing(sale, listingId);
		Result section = getAgreementListings(sale, chain.agreementId());

		assertEquals(200, read.status(), read.rawBody());
		assertEquals("ARCHIVED", read.body().path("state").asString(), read.rawBody());
		assertEquals(200, section.status(), section.rawBody());
		assertEquals("ARCHIVED", section.body().path("listings").path(0).path("state").asString(), section.rawBody());
		assertEquals("[\"ENDED\"]", section.body().path("creation").path("reasonGroups").toString(), section.rawBody());
		assertEquals("UNPUBLISHED", jdbc.queryForObject("select state from listings where id = ?", String.class,
				listingId));
		assertNoListingWritten(before);
	}

	/** LC-11, listing-detail BR-15: the code of the 201 is the code every read answers. */
	@Test
	void getListing_code_sameAcrossReads() throws Exception {
		Chain chain = inEffect();
		Result created = createListing(sale, chain.agreementId());
		assertEquals(201, created.status(), created.rawBody());
		String listingId = created.body().path("listingId").asString();
		String code = created.body().path("code").asString();

		for (int read = 0; read < 2; read++) {
			assertEquals(code, getListing(sale, listingId).body().path("code").asString());
			assertEquals(code, getAgreementListings(sale, chain.agreementId()).body().path("listings").path(0)
					.path("code").asString());
		}
	}

	/** Section 4: no token, 401 UNAUTHORIZED on both reads. */
	@Test
	void reads_noToken_unauthorized() throws Exception {
		Chain chain = inEffect();
		String listingId = newListing(sale, chain.agreementId());

		assertCode(401, "UNAUTHORIZED", getListing(null, listingId));
		assertCode(401, "UNAUTHORIZED", getAgreementListings(null, chain.agreementId()));
	}

	private static List<String> names(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.properties().forEach(entry -> names.add(entry.getKey()));
		return names.stream().sorted().toList();
	}

	private static String bodyWithoutCorrelation(Result answer) {
		return answer.body().path("code").asString() + "|" + answer.body().path("message").asString() + "|"
				+ answer.body().path("errors");
	}
}
