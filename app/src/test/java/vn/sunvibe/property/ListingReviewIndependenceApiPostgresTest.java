package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;

/**
 * The reviewer's independence over HTTP on PostgreSQL (listing-publish BR-7, US-4 AC-4, E-6, E-11; listing BR-16; E06-S4
 * spec E06S4-D3, E06S4-D4, E06S4-D5 step 8; RV-2, RV-3): the creator, an editor, the submitter and the authors of a
 * copy's source are refused as AUTHOR, the agreement's assigned Sale as ASSIGNED_SALE whatever roles they hold, both in
 * contract order; a reassignment moves the refusal to the new holder at once; an ADMIN who is neither decides. Users who
 * hold SALE beside LISTING_REVIEWER or ADMIN are made with {@code survey.user}. A refusal writes nothing, no access log
 * row included. Needs Docker.
 */
class ListingReviewIndependenceApiPostgresTest extends ListingReviewApiTestBase {

	@Override
	String phonePrefix() {
		return "0900";
	}

	/** RV-2: the listing's creator, and an editor of the revision's content, are refused as AUTHOR. */
	@Test
	void decide_creatorOrEditor_reviewerNotIndependent() throws Exception {
		TestUser creator = survey.user("Independence Creator", "SALE", "LISTING_REVIEWER");
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), creator);
		String created = newListing(creator, chain.agreementId());
		fixture.checklists().reassign(leader, chain.leadId(), sale);
		completedAndSubmitted(sale, created, "1");

		TestUser editor = survey.user("Independence Editor", "SALE", "LISTING_REVIEWER");
		Drafting edited = listingInEffect();
		fixture.checklists().reassign(leader, edited.chain().leadId(), editor);
		assertEquals(200, saveContent(editor, edited.listingId(), "1",
				completeContentBody(dataVersion(edited.listingId()))).status());
		fixture.checklists().reassign(leader, edited.chain().leadId(), sale);
		assertEquals(200, captureTerms(sale, edited.listingId(), "1", dataVersion(edited.listingId()), "IN_EFFECT")
				.status());
		assertEquals(200, submit(sale, edited.listingId(), "1", dataVersion(edited.listingId())).status());
		Written before = written();

		assertRefused(List.of("reasons.AUTHOR"), decide(creator, created, "1", approval(dataVersion(created))));
		assertRefused(List.of("reasons.AUTHOR"), decide(editor, edited.listingId(), "1",
				approval(dataVersion(edited.listingId()))));

		assertEquals(before, written());
	}

	/** RV-2, E06S4-D3 (b): a submission after a reassignment, the lead then reassigned again: the submitter is an author. */
	@Test
	void decide_submitterOnly_reviewerNotIndependent() throws Exception {
		TestUser submitter = survey.user("Independence Submitter", "SALE", "LISTING_REVIEWER");
		Drafting drafting = listingInEffect();
		completeDraft(drafting);
		fixture.checklists().reassign(leader, drafting.chain().leadId(), submitter);
		assertEquals(200, submit(submitter, drafting.listingId(), "1", dataVersion(drafting.listingId())).status());
		fixture.checklists().reassign(leader, drafting.chain().leadId(), sale);
		Written before = written();

		Result answer = decide(submitter, drafting.listingId(), "1", approval(dataVersion(drafting.listingId())));

		assertRefused(List.of("reasons.AUTHOR"), answer);
		assertEquals(before, written());
	}

	/** RV-2, E06S4-D3: the author of a returned revision is an author of its copy, through the copy chain. */
	@Test
	void decide_copySourceAuthor_reviewerNotIndependent() throws Exception {
		TestUser author = survey.user("Independence Source Author", "SALE", "LISTING_REVIEWER");
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), author);
		String listingId = newListing(author, chain.agreementId());
		completedAndSubmitted(author, listingId, "1");
		fixture.checklists().reassign(leader, chain.leadId(), sale);
		decided(reviewer, listingId, "1", returnBody(dataVersion(listingId), List.of("OTHER"), NOTE));
		assertEquals(201, copy(sale, listingId, "1", dataVersion(listingId)).status());
		assertEquals(200, submit(sale, listingId, "2", dataVersion(listingId)).status());
		Written before = written();

		Result answer = decide(author, listingId, "2", approval(dataVersion(listingId)));

		assertRefused(List.of("reasons.AUTHOR"), answer);
		assertEquals(before, written());
	}

	/** RV-2, US-4 AC-4: the assigned Sale who also holds ADMIN and wrote nothing is refused as ASSIGNED_SALE. */
	@Test
	void decide_assigneeHoldingAdmin_assignedSale() throws Exception {
		TestUser assignee = survey.user("Independence Assignee Admin", "SALE", "ADMIN");
		Drafting drafting = pendingDrafting();
		fixture.checklists().reassign(leader, drafting.chain().leadId(), assignee);
		Written before = written();

		Result answer = decide(assignee, drafting.listingId(), "1", approval(dataVersion(drafting.listingId())));

		assertRefused(List.of("reasons.ASSIGNED_SALE"), answer);
		assertEquals(before, written());
	}

	/** RV-2: an author who is also the assignee: both reasons, AUTHOR first. */
	@Test
	void decide_authorAndAssignee_bothReasonsInOrder() throws Exception {
		TestUser author = survey.user("Independence Author Assignee", "SALE", "LISTING_REVIEWER");
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), author);
		String listingId = newListing(author, chain.agreementId());
		completedAndSubmitted(author, listingId, "1");
		Written before = written();

		Result answer = decide(author, listingId, "1", approval(dataVersion(listingId)));

		assertRefused(List.of("reasons.AUTHOR", "reasons.ASSIGNED_SALE"), answer);
		assertEquals(before, written());
	}

	/** RV-2: an ADMIN who is neither an author nor the assignee decides. */
	@Test
	void decide_adminNeither_approved() throws Exception {
		String listingId = pending();

		Result answer = decide(admin, listingId, "1", approval(dataVersion(listingId)));

		assertEquals(200, answer.status(), answer.rawBody());
		assertEquals("APPROVED", revisionStatus(listingId, 1));
		assertEquals(admin.userId(), jdbc.queryForObject("select decided_by from listing_revision_reviews where"
				+ " listing_id = ?", String.class, listingId));
	}

	/** RV-3, E-11: the lead reassigned while the revision waits: the new holder, a reviewer, is refused; it stays pending. */
	@Test
	void decide_reassignedWhilePending_newHolderRefusedRevisionPending() throws Exception {
		TestUser holder = survey.user("Independence New Holder", "SALE", "LISTING_REVIEWER");
		Drafting drafting = pendingDrafting();
		fixture.checklists().reassign(leader, drafting.chain().leadId(), holder);
		Written before = written();

		Result answer = decide(holder, drafting.listingId(), "1", approval(dataVersion(drafting.listingId())));

		assertRefused(List.of("reasons.ASSIGNED_SALE"), answer);
		assertEquals(before, written());
		assertEquals("PENDING_REVIEW", revisionStatus(drafting.listingId(), 1));
	}

	/** RV-2: the creator opens the location: refused as AUTHOR; no access log row. */
	@Test
	void unitLocation_author_reviewerNotIndependentNoLog() throws Exception {
		TestUser creator = survey.user("Location Creator", "SALE", "LISTING_REVIEWER");
		Chain chain = inEffect();
		fixture.checklists().reassign(leader, chain.leadId(), creator);
		String listingId = newListing(creator, chain.agreementId());
		fixture.checklists().reassign(leader, chain.leadId(), sale);
		completedAndSubmitted(sale, listingId, "1");
		Written before = written();

		Result answer = openLocation(creator, listingId, "1");

		assertRefused(List.of("reasons.AUTHOR"), answer);
		assertEquals(before, written());
		assertEquals(0, accessLogRows(listingId));
	}

	/** RV-2: the assigned Sale, also holding ADMIN and no author, opens the location: ASSIGNED_SALE; no log row. */
	@Test
	void unitLocation_assigneeHoldingAdmin_reviewerNotIndependentNoLog() throws Exception {
		TestUser assignee = survey.user("Location Assignee Admin", "SALE", "ADMIN");
		Drafting drafting = pendingDrafting();
		fixture.checklists().reassign(leader, drafting.chain().leadId(), assignee);
		Written before = written();

		Result answer = openLocation(assignee, drafting.listingId(), "1");

		assertRefused(List.of("reasons.ASSIGNED_SALE"), answer);
		assertEquals(before, written());
		assertEquals(0, accessLogRows(drafting.listingId()));
	}

	private static void assertRefused(List<String> entries, Result answer) {
		assertCode(403, "REVIEWER_NOT_INDEPENDENT", answer);
		assertEquals(entries, fields(answer), answer.rawBody());
	}

	/** The revision's content saved, the terms in effect captured and the revision submitted by the caller. */
	private void completedAndSubmitted(TestUser caller, String listingId, String revisionNo) throws Exception {
		assertEquals(200, saveContent(caller, listingId, revisionNo, completeContentBody(dataVersion(listingId)))
				.status());
		assertEquals(200, captureTerms(caller, listingId, revisionNo, dataVersion(listingId), "IN_EFFECT").status());
		Result answer = submit(caller, listingId, revisionNo, dataVersion(listingId));
		assertEquals(200, answer.status(), answer.rawBody());
	}
}
