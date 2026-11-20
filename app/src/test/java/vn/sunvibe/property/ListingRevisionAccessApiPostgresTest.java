package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * Who may act on a listing's revisions, over HTTP on PostgreSQL (listing-publish E06-S2 RD-9; listing BR-5, BR-16; US-1
 * AC-5; E-3, E-11; listing-publish section 2; E06S2-D2): only the SALE or SALE_LEADER who holds the basis lead now
 * drafts; another SALE gets 404 everywhere; a SALE_LEADER who is not the assignee, the managers, ADMIN (SUPER_ADMIN
 * through the hierarchy) and LISTING_REVIEWER read the revision and are refused the commands and the terms sources;
 * roles without rights get 403 on every path; an id the listing does not have answers 404; a reassignment moves the
 * rights at once. A refused call writes nothing. Needs Docker.
 */
class ListingRevisionAccessApiPostgresTest extends ListingRevisionApiTestBase {

	@Override
	String phonePrefix() {
		return "0912";
	}

	/** RD-9: no token, 401 on each of the seven paths; nothing written. */
	@Test
	void paths_noToken_unauthorizedOnEveryPath() throws Exception {
		String listingId = listingInEffect().listingId();
		RevisionRows before = revisionRows(listingId);

		assertEach(401, "UNAUTHORIZED", everyPath(null, listingId, "1", dataVersion(listingId)));

		assertEquals(before, revisionRows(listingId));
	}

	/** RD-9, listing BR-5: a SALE who is not the assignee gets 404 on every path; the assignee reads the revision. */
	@Test
	void paths_saleNotAssignee_notFoundReadOnlyWhileAssignee() throws Exception {
		String listingId = listingInEffect().listingId();
		RevisionRows before = revisionRows(listingId);

		assertEach(404, "NOT_FOUND", commandsAndSources(otherSale, listingId, "1", dataVersion(listingId)));
		Result otherRead = getRevision(otherSale, listingId, "1");
		Result unknownRead = getRevision(sale, UNKNOWN, "1");

		assertCode(404, "NOT_FOUND", otherRead);
		assertEquals(bodyWithoutCorrelation(unknownRead), bodyWithoutCorrelation(otherRead));
		assertEquals(before, revisionRows(listingId));
		Result assigneeRead = getRevision(sale, listingId, "1");
		assertEquals(200, assigneeRead.status(), assigneeRead.rawBody());
		assertEquals("DRAFT", assigneeRead.body().path("status").asString());
	}

	/** RD-9, listing-publish section 2: a SALE_LEADER who holds the basis lead drafts, snapshots, submits and withdraws. */
	@Test
	void leaderAssignee_everyCommandAndSources_acts() throws Exception {
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), leader);
		String listingId = newListing(leader, chain.agreementId());

		Result sources = getTermsSources(leader, listingId);
		assertEquals(200, sources.status(), sources.rawBody());
		assertFalse(sources.body().path("inEffect").isNull(), sources.rawBody());
		assertStatus(200, saveContent(leader, listingId, "1", completeContentBody(dataVersion(listingId))));
		assertStatus(200, captureTerms(leader, listingId, "1", dataVersion(listingId), "IN_EFFECT"));
		assertStatus(200, getRevision(leader, listingId, "1"));
		assertStatus(200, submit(leader, listingId, "1", dataVersion(listingId)));
		assertStatus(200, withdraw(leader, listingId, "1", dataVersion(listingId)));
		Result started = startRevision(leader, listingId, dataVersion(listingId));

		assertEquals(201, started.status(), started.rawBody());
		assertEquals(2, started.body().path("revisionNo").asInt());
		assertEquals(List.of("CREATED", "REVISION_SAVED", "TERMS_CAPTURED", "REVISION_SUBMITTED", "REVISION_WITHDRAWN",
				"REVISION_STARTED"), jdbc.queryForList("select action from listing_history where listing_id = ?"
				+ " order by occurred_at, action", String.class, listingId));
		assertEquals(List.of(leader.userId()), jdbc.queryForList("select distinct actor_user_id from listing_history"
				+ " where listing_id = ?", String.class, listingId));
	}

	/** RD-9, listing BR-5: a SALE_LEADER who is not the assignee reads (a manager's scope) and does not draft. */
	@Test
	void leaderNotAssignee_readsRevision_notFoundOnCommandsAndSources() throws Exception {
		String listingId = listingInEffect().listingId();
		RevisionRows before = revisionRows(listingId);

		Result read = getRevision(leader, listingId, "1");

		assertEquals(200, read.status(), read.rawBody());
		assertEach(404, "NOT_FOUND", commandsAndSources(leader, listingId, "1", dataVersion(listingId)));
		assertEquals(before, revisionRows(listingId));
	}

	/** RD-9: SALES_MANAGER, ADMIN, SUPER_ADMIN and LISTING_REVIEWER: 403 on the commands and sources; they read. */
	@Test
	void managersAdminsReviewer_forbiddenOnCommandsAndSources_readRevision() throws Exception {
		String listingId = listingInEffect().listingId();
		TestUser superAdmin = survey.user("Revision Super Admin", "SUPER_ADMIN");
		RevisionRows before = revisionRows(listingId);

		for (TestUser caller : List.of(manager, admin, superAdmin, reviewer)) {
			assertEach(403, "FORBIDDEN", commandsAndSources(caller, listingId, "1", dataVersion(listingId)));
			Result read = getRevision(caller, listingId, "1");
			assertEquals(200, read.status(), caller.userId() + " " + read.rawBody());
		}
		assertEquals(before, revisionRows(listingId));
	}

	/** RD-9, listing-publish section 2: CUSTOMER, LEADER and MANAGER have no right here: 403 on every path. */
	@Test
	void rolesWithoutRights_forbiddenOnEveryPath() throws Exception {
		String listingId = listingInEffect().listingId();
		TestUser oldLeader = survey.user("Revision Old Leader", "LEADER");
		TestUser oldManager = survey.user("Revision Old Manager", "MANAGER");
		RevisionRows before = revisionRows(listingId);

		for (TestUser caller : List.of(customer, oldLeader, oldManager)) {
			assertEach(403, "FORBIDDEN", everyPath(caller, listingId, "1", dataVersion(listingId)));
		}
		assertEquals(before, revisionRows(listingId));
	}

	/**
	 * RD-9, listing BR-5: an unknown or malformed listing id, a number the listing does not have ({@code 0}, {@code 01},
	 * {@code abc}, ten digits, {@code 99}) and a number only another listing has: 404 on every path; nothing written.
	 */
	@Test
	void malformedOrForeignIds_notFoundOnEveryPath() throws Exception {
		Drafting first = listingInEffect();
		Drafting second = listingInEffect();
		String listingId = first.listingId();
		String otherId = second.listingId();
		completeDraft(second);
		assertStatus(200, submit(sale, otherId, "1", dataVersion(otherId)));
		assertStatus(200, withdraw(sale, otherId, "1", dataVersion(otherId)));
		assertEquals(201, startRevision(sale, otherId, dataVersion(otherId)).status());
		RevisionRows firstBefore = revisionRows(listingId);
		RevisionRows secondBefore = revisionRows(otherId);
		long version = dataVersion(listingId);

		for (String unknown : List.of(UNKNOWN, listingId.toLowerCase(Locale.ROOT), "not-a-listing")) {
			assertEach(404, "NOT_FOUND", everyPath(sale, unknown, "1", version));
		}
		for (String number : List.of("0", "01", "abc", "1234567890", "99", "2")) {
			assertEach(404, "NOT_FOUND", numberedPaths(sale, listingId, number, version));
		}

		assertEquals(firstBefore, revisionRows(listingId));
		assertEquals(secondBefore, revisionRows(otherId));
	}

	/**
	 * RD-9, listing BR-16: after a reassignment the former assignee gets 404 on every path, the pending revision and its
	 * history rows unchanged; the new assignee reads it, its terms sources, and withdraws it.
	 */
	@Test
	void reassignment_formerNotFoundNewActs_pendingAndHistoryUnchanged() throws Exception {
		Drafting drafting = listingInEffect();
		String listingId = drafting.listingId();
		completeDraft(drafting);
		assertStatus(200, submit(sale, listingId, "1", dataVersion(listingId)));
		Map<String, Object> pending = revisionRow(listingId);
		List<Map<String, Object>> history = historyRows(listingId);
		long version = dataVersion(listingId);

		fixture.checklists().reassign(leader, drafting.chain().leadId(), otherSale);

		assertEach(404, "NOT_FOUND", everyPath(sale, listingId, "1", version));
		assertEquals(pending, revisionRow(listingId));
		assertEquals(history, historyRows(listingId));
		assertEquals(version, dataVersion(listingId));
		Result read = getRevision(otherSale, listingId, "1");
		assertEquals(200, read.status(), read.rawBody());
		assertEquals("PENDING_REVIEW", read.body().path("status").asString());
		assertStatus(200, getTermsSources(otherSale, listingId));
		Result withdrawn = withdraw(otherSale, listingId, "1", version);
		assertEquals(200, withdrawn.status(), withdrawn.rawBody());
		assertEquals(Set.of(sale.userId(), otherSale.userId()), Set.copyOf(jdbc.queryForList(
				"select actor_user_id from listing_history where listing_id = ?", String.class, listingId)));
		assertEquals(otherSale.userId(), jdbc.queryForObject("select actor_user_id from listing_history"
				+ " where listing_id = ? and action = 'REVISION_WITHDRAWN'", String.class, listingId));
	}

	// ==================== the paths ====================

	/** The revision read and the four commands that name a revision number. */
	private Map<String, Result> numberedPaths(TestUser caller, String listingId, String revisionNo, long version)
			throws Exception {
		Map<String, Result> answers = new LinkedHashMap<>();
		answers.put("read", getRevision(caller, listingId, revisionNo));
		answers.put("save", saveContent(caller, listingId, revisionNo, completeContentBody(version)));
		answers.put("terms", captureTerms(caller, listingId, revisionNo, version, "IN_EFFECT"));
		answers.put("submit", submit(caller, listingId, revisionNo, version));
		answers.put("withdraw", withdraw(caller, listingId, revisionNo, version));
		return answers;
	}

	/** The five commands, then the terms sources. */
	private Map<String, Result> commandsAndSources(TestUser caller, String listingId, String revisionNo, long version)
			throws Exception {
		Map<String, Result> answers = new LinkedHashMap<>();
		answers.put("start", startRevision(caller, listingId, version));
		answers.put("save", saveContent(caller, listingId, revisionNo, completeContentBody(version)));
		answers.put("terms", captureTerms(caller, listingId, revisionNo, version, "IN_EFFECT"));
		answers.put("submit", submit(caller, listingId, revisionNo, version));
		answers.put("withdraw", withdraw(caller, listingId, revisionNo, version));
		answers.put("sources", getTermsSources(caller, listingId));
		return answers;
	}

	/** The seven paths: the revision read, the five commands and the terms sources. */
	private Map<String, Result> everyPath(TestUser caller, String listingId, String revisionNo, long version)
			throws Exception {
		Map<String, Result> answers = new LinkedHashMap<>();
		answers.put("read", getRevision(caller, listingId, revisionNo));
		answers.putAll(commandsAndSources(caller, listingId, revisionNo, version));
		return answers;
	}

	/** Each answer with the status and the code, in the order of the calls. */
	private static void assertEach(int status, String code, Map<String, Result> answers) {
		answers.forEach((path, answer) -> {
			assertEquals(status, answer.status(), path + " " + answer.rawBody());
			assertEquals(code, answer.body().path("code").asString(), path + " " + answer.rawBody());
		});
	}

	private static void assertStatus(int status, Result answer) {
		assertEquals(status, answer.status(), answer.rawBody());
	}

	private static String bodyWithoutCorrelation(Result answer) {
		return answer.body().path("code").asString() + "|" + answer.body().path("message").asString() + "|"
				+ answer.body().path("errors");
	}

	private Map<String, Object> revisionRow(String listingId) {
		return jdbc.queryForMap("select * from listing_revisions where listing_id = ? and revision_no = 1", listingId);
	}

	private List<Map<String, Object>> historyRows(String listingId) {
		return jdbc.queryForList("select * from listing_history where listing_id = ? order by occurred_at, action",
				listingId);
	}
}
