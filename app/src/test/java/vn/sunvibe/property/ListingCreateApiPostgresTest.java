package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.listing.core.application.command.ListingCreateCommand;
import vn.sunvibe.listing.core.application.usecase.ListingViewer;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;

/**
 * The listing create over HTTP on PostgreSQL (listing-publish US-1 AC-1 to AC-4, BR-1, LP-1, E-1 to E-3; E06-S1 spec 3.1,
 * section 4, E06S1-D2 to E06S1-D4, E06S1-D16; LC-1 to LC-6, LC-14): the assigned Sale creates under an agreement whose
 * advertising answer allows it, every refusal answers its code and writes nothing, and no outbox row is written. Needs
 * Docker.
 */
class ListingCreateApiPostgresTest extends ListingApiTestBase {

	@Override
	String phonePrefix() {
		return "0918";
	}

	/** LC-1, LC-14: 201 with the code; the listing, revision 1 DRAFT and one CREATED row by the Sale; no outbox row. */
	@Test
	void create_saleAssignee_createdUnpublishedDraftAndHistory() throws Exception {
		Chain chain = inEffect();
		int outbox = outboxCount();

		Result answer = createListing(sale, chain.agreementId());

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(outbox, outboxCount());
		String listingId = answer.body().path("listingId").asString();
		String code = answer.body().path("code").asString();
		assertTrue(code.matches(CODE_FORM), code);
		assertTrue(listingId.matches("^[0-9A-Z]{26}$"), listingId);
		Map<String, Object> listing = jdbc.queryForMap("select * from listings where id = ?", listingId);
		assertEquals(code, listing.get("code"));
		assertEquals("UNPUBLISHED", listing.get("state"));
		assertEquals("LONG_TERM_RENT", listing.get("transaction_type"));
		assertEquals(chain.unitId(), listing.get("unit_id"));
		assertEquals(chain.agreementId(), listing.get("agreement_id"));
		assertEquals(sale.userId(), listing.get("created_by"));
		assertEquals(0L, ((Number) listing.get("row_version")).longValue());
		Map<String, Object> revision = jdbc.queryForMap("select * from listing_revisions where listing_id = ?", listingId);
		assertEquals("DRAFT", revision.get("status"));
		assertEquals(1, ((Number) revision.get("revision_no")).intValue());
		assertEquals(sale.userId(), revision.get("created_by"));
		assertEquals(List.of("CREATED 1 " + sale.userId()), jdbc.queryForList("select action || ' ' || revision_no || ' '"
				+ " || actor_user_id from listing_history where listing_id = ?", String.class, listingId));
	}

	/** LC-1: a SALE_LEADER who holds the basis lead creates too. */
	@Test
	void create_saleLeaderAssignee_created() throws Exception {
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), leader);

		Result answer = createListing(leader, chain.agreementId());

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals(leader.userId(), jdbc.queryForObject("select created_by from listings where id = ?", String.class,
				answer.body().path("listingId").asString()));
	}

	/** LC-2, listing-publish LP-1: under an activated version whose start is ahead, NOT_STARTED alone: created. */
	@Test
	void create_notStartedVersion_created() throws Exception {
		Chain chain = activated(today().plusDays(10), today().plusMonths(6));

		Result answer = createListing(sale, chain.agreementId());

		assertEquals(201, answer.status(), answer.rawBody());
	}

	/** LC-3, LC-14: each refusal answers E05's groups in E05's order, names no lead and writes nothing. */
	@Test
	void create_refusedAgreements_groupsNothingWritten() throws Exception {
		Chain drafting = drafted(today(), today().plusMonths(6));
		assertRefused(drafting, List.of("NOT_IN_EFFECT"));

		Chain suspended = inEffect();
		suspended(admin, suspended.agreementId());
		assertRefused(suspended, List.of("SUSPENDED"));

		Chain terminated = inEffect();
		Result termination = terminate(sale, terminated.agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		assertRefused(terminated, List.of("ENDED"));

		Chain notStarted = activated(today().plusDays(10), today().plusMonths(6));
		coOwnerOf(notStarted);
		assertRefused(notStarted, List.of("NOT_STARTED", "OWNER"));
	}

	/** LC-4: a second create on the agreement answers LISTING_EXISTS with no entry and no id; nothing written. */
	@Test
	void create_secondOnTheAgreement_existsNothingWritten() throws Exception {
		Chain chain = inEffect();
		String first = newListing(sale, chain.agreementId());
		ListingRows before = listingRows();

		Result second = createListing(sale, chain.agreementId());

		assertCode(409, "LISTING_EXISTS", second);
		assertEquals("This agreement already has a listing.", second.body().path("message").asString());
		assertEquals(0, second.body().path("errors").size(), second.rawBody());
		assertFalse(second.rawBody().contains(first), second.rawBody());
		assertNoListingWritten(before);
	}

	/** LC-5: A's listing stays stored UNPUBLISHED but reads ARCHIVED once A ended, and B's create passes. */
	@Test
	void create_newAgreementAfterTermination_createdOldArchived() throws Exception {
		Chain chain = inEffect();
		String oldListing = newListing(sale, chain.agreementId());
		Result termination = terminate(sale, chain.agreementId(), unitLet());
		assertEquals(204, termination.status(), termination.rawBody());
		String next = create(sale, chain.leadId(), chain.unitId());
		saveComplete(next, today(), today().plusMonths(6));
		send(next);
		confirm(chain.owner(), next, 1);
		Result activation = activate(admin, next);
		assertEquals(204, activation.status(), activation.rawBody());

		Result answer = createListing(sale, next);

		assertEquals(201, answer.status(), answer.rawBody());
		assertEquals("UNPUBLISHED", jdbc.queryForObject("select state from listings where id = ?", String.class,
				oldListing));
		Result old = getListing(sale, oldListing);
		assertEquals(200, old.status(), old.rawBody());
		assertEquals("ARCHIVED", old.body().path("state").asString(), old.rawBody());
	}

	/** LC-6: a role outside SALE and SALE_LEADER gets 403 for the agreement and for an unknown id alike. */
	@Test
	void create_otherRolesWellFormedBody_forbiddenForAnyAgreement() throws Exception {
		Chain chain = inEffect();
		ListingRows before = listingRows();

		for (TestUser caller : List.of(manager, admin, reviewer, customer)) {
			for (String agreementId : List.of(chain.agreementId(), UNKNOWN)) {
				assertCode(403, "FORBIDDEN", createListing(caller, agreementId));
			}
		}
		assertNoListingWritten(before);
	}

	/** LC-6, listing BR-5: another SALE and an unknown agreement get the same 404 body; nothing written. */
	@Test
	void create_notAssigneeOrUnknown_sameNotFoundNothingWritten() throws Exception {
		Chain chain = inEffect();
		ListingRows before = listingRows();

		Result other = createListing(otherSale, chain.agreementId());
		Result unknown = createListing(sale, UNKNOWN);

		assertCode(404, "NOT_FOUND", other);
		assertCode(404, "NOT_FOUND", unknown);
		assertEquals(bodyWithoutCorrelation(unknown), bodyWithoutCorrelation(other));
		assertNoListingWritten(before);
	}

	/** Section 4: a body without a valid id is 400; an unreadable one MALFORMED_REQUEST; extra properties are ignored. */
	@Test
	void create_badBody_validationOrMalformedExtraIgnored() throws Exception {
		Chain chain = inEffect();
		ListingRows before = listingRows();

		assertValidation(api.post(sale, LISTINGS, Map.of()), "agreementId", null);
		assertValidation(createListing(sale, chain.agreementId().toLowerCase(java.util.Locale.ROOT)), "agreementId", null);
		assertMalformed(postRaw(sale, LISTINGS, "{\"agreementId\": "));
		assertNoListingWritten(before);

		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("agreementId", chain.agreementId());
		extra.put("unitId", "01JEOTHERUNIT0000000000000");
		extra.put("code", "TD-2345-6789");
		extra.put("state", "PUBLISHED");
		extra.put("createdBy", otherSale.userId());
		Result answer = api.post(sale, LISTINGS, extra);

		assertEquals(201, answer.status(), answer.rawBody());
		Map<String, Object> listing = jdbc.queryForMap("select * from listings where id = ?",
				answer.body().path("listingId").asString());
		assertEquals(chain.unitId(), listing.get("unit_id"));
		assertNotEquals("TD-2345-6789", listing.get("code"));
		assertEquals("UNPUBLISHED", listing.get("state"));
		assertEquals(sale.userId(), listing.get("created_by"));
	}

	/** Section 4: no token, 401 UNAUTHORIZED. */
	@Test
	void create_noToken_unauthorized() throws Exception {
		Chain chain = inEffect();
		ListingRows before = listingRows();

		Result answer = createListing(null, chain.agreementId());

		assertCode(401, "UNAUTHORIZED", answer);
		assertNoListingWritten(before);
	}

	// ==================== races (LC-4, LC-7; plan decision E06S1-P6) ====================

	/** LC-4: two creates of the agreement released together: one 201, one 409 LISTING_EXISTS, one listing. */
	@Test
	void create_twoConcurrent_oneCreatedOneExists() throws Exception {
		Chain chain = inEffect();
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Result>> creates = new ArrayList<>();
		for (int side = 0; side < 2; side++) {
			creates.add(executor.submit(() -> {
				start.await(30, TimeUnit.SECONDS);
				return createListing(sale, chain.agreementId());
			}));
		}

		start.countDown();
		List<Result> answers = new ArrayList<>();
		for (Future<Result> create : creates) {
			answers.add(create.get(60, TimeUnit.SECONDS));
		}

		assertEquals(List.of(201, 409), answers.stream().map(Result::status).sorted().toList(), answers.toString());
		Result refused = answers.stream().filter(answer -> answer.status() == 409).findFirst().orElseThrow();
		assertEquals("LISTING_EXISTS", refused.body().path("code").asString(), refused.rawBody());
		assertEquals(1, listingsOf(chain.agreementId()));
	}

	/** LC-4: an open listing not committed yet makes the create wait on the index, which then answers LISTING_EXISTS. */
	@Test
	void create_uncommittedOpenListing_existsOnceItCommits() throws Exception {
		Chain chain = inEffect();
		String held = ids.generateId();
		Future<Result> create;
		try (Hold hold = new Hold(() -> jdbc.update("insert into listings (id, code, unit_id, transaction_type,"
				+ " agreement_id, state, created_by, created_at, updated_at) values (?, ?, ?, 'LONG_TERM_RENT', ?,"
				+ " 'UNPUBLISHED', ?, ?, ?)", held, ListingCode.draw(new SecureRandom()).value(), chain.unitId(),
				chain.agreementId(), sale.userId(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now())))) {
			create = executor.submit(() -> createListing(sale, chain.agreementId()));
			awaitWaiters(1);
			assertFalse(create.isDone());
		}

		Result answer = create.get(60, TimeUnit.SECONDS);

		assertCode(409, "LISTING_EXISTS", answer);
		assertEquals(List.of(held), jdbc.queryForList("select id from listings where agreement_id = ?", String.class,
				chain.agreementId()));
		assertEquals(0, count("select count(*) from listing_revisions r join listings l on l.id = r.listing_id"
				+ " where l.agreement_id = ?", chain.agreementId()));
		assertEquals(0, count("select count(*) from listing_history h join listings l on l.id = h.listing_id"
				+ " where l.agreement_id = ?", chain.agreementId()));
	}

	/** LC-7, first order: a suspension committed before the create refuses it with SUSPENDED; nothing written. */
	@Test
	void create_suspensionCommittedFirst_refusedSuspended() throws Exception {
		Chain chain = inEffect();
		suspended(admin, chain.agreementId());
		ListingRows before = listingRows();

		Result answer = createListing(sale, chain.agreementId());

		assertCode(409, "LISTING_NOT_ALLOWED", answer);
		assertEquals("reasonGroups.SUSPENDED", answer.body().path("errors").path(0).path("field").asString(),
				answer.rawBody());
		assertEquals(1, answer.body().path("errors").size(), answer.rawBody());
		assertNoListingWritten(before);
	}

	/** LC-7, second order: the create holds E05's share lock, so a suspension waits until the create commits. */
	@Test
	void create_heldShareLock_suspensionWaitsThenCommits() throws Exception {
		Chain chain = inEffect();
		String id = chain.agreementId();
		Future<Result> suspension;
		try (Hold hold = new Hold(() -> listingCommands.create(new ListingCreateCommand(id),
				new ListingViewer(sale.userId(), Set.of("ROLE_SALE"))))) {
			suspension = executor.submit(() -> suspendAgreement(admin, id, "COMPLAINT", "Tenant reported a different price."));
			awaitWaiters(1);
			assertFalse(suspension.isDone());
		}

		Result answer = suspension.get(60, TimeUnit.SECONDS);

		assertEquals(204, answer.status(), answer.rawBody());
		assertEquals(1, listingsOf(id));
		assertTrue(answer(chain).reasonGroups().contains("SUSPENDED"), answer(chain).toString());
	}

	private int listingsOf(String agreementId) {
		return count("select count(*) from listings where agreement_id = ?", agreementId);
	}

	/** 409 LISTING_NOT_ALLOWED with one entry per group in this order, no lead in the body, nothing written. */
	private void assertRefused(Chain chain, List<String> groups) throws Exception {
		ListingRows before = listingRows();

		Result answer = createListing(sale, chain.agreementId());

		assertCode(409, "LISTING_NOT_ALLOWED", answer);
		assertEquals("A listing cannot be created for this unit now.", answer.body().path("message").asString());
		List<String> fields = new ArrayList<>();
		answer.body().path("errors").forEach(entry -> {
			fields.add(entry.path("field").asString());
			assertEquals("This group stops the listing.", entry.path("message").asString(), answer.rawBody());
		});
		assertEquals(groups.stream().map(group -> "reasonGroups." + group).toList(), fields, answer.rawBody());
		assertFalse(answer.rawBody().contains(chain.leadId()), answer.rawBody());
		assertFalse(answer.rawBody().contains("Intake Sale"), answer.rawBody());
		assertNoListingWritten(before);
	}

	private static String bodyWithoutCorrelation(Result answer) {
		return answer.body().path("code").asString() + "|" + answer.body().path("message").asString() + "|"
				+ answer.body().path("errors");
	}
}
