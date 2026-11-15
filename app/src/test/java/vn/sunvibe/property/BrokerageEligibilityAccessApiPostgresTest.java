package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * Who may read a lead's brokerage agreement eligibility (brokerage-agreement BR-16; E05-S1 spec 3.3, S1-D5, EL-8): the
 * staff who may see the lead, a lead they cannot see answering as an unknown one; the flag of the assigned Sale. In the
 * property test package to share the intake fixtures and their Spring context (plan decision E05S1-P1). Needs Docker.
 */
class BrokerageEligibilityAccessApiPostgresTest extends IntakeApiTestBase {

	private static final String UNKNOWN_LEAD = "01JUNKNOWN0000000000000000";

	@Override
	String phonePrefix() {
		return "0954";
	}

	private static String path(String leadId) {
		return LEADS + "/" + leadId + "/brokerage-eligibility";
	}

	/** A lead of {@code sale} with a completed intake. */
	private String done() throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		checked(passed, owner());
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		return passed.leadId();
	}

	private boolean assignedFlag(TestUser caller, String leadId) throws Exception {
		Result answer = api.get(caller, path(leadId));
		assertEquals(200, answer.status(), answer.rawBody());
		assertFalse(answer.body().path("units").isEmpty(), answer.rawBody());
		return answer.body().path("viewerIsAssignedSale").asBoolean();
	}

	private static void assertNotFound(Result answer) {
		assertEquals(404, answer.status(), answer.rawBody());
		assertEquals("NOT_FOUND", answer.body().path("code").asString(), answer.rawBody());
		assertEquals("The resource was not found.", answer.body().path("message").asString(), answer.rawBody());
		assertEquals("[]", answer.body().path("errors").toString(), answer.rawBody());
	}

	@Test
	void access_assignedSale_trueFlag() throws Exception {
		String lead = done();

		assertTrue(assignedFlag(sale, lead));
	}

	@Test
	void access_otherSale_notFoundLikeUnknown() throws Exception {
		String lead = done();

		Result hidden = api.get(otherSale, path(lead));
		Result unknown = api.get(sale, path(UNKNOWN_LEAD));

		assertNotFound(hidden);
		assertNotFound(unknown);
	}

	@Test
	void access_managersWhoSeeTheLead_falseFlag() throws Exception {
		String lead = done();

		for (TestUser reader : List.of(leader, manager, admin)) {
			assertFalse(assignedFlag(reader, lead), reader.toString());
		}
	}

	@Test
	void access_saleLeaderAssignee_trueFlag() throws Exception {
		String lead = done();

		fixture.checklists().reassign(leader, lead, leader);

		assertTrue(assignedFlag(leader, lead));
		assertNotFound(api.get(sale, path(lead)));
	}

	@Test
	void access_superAdmin_throughAdmin() throws Exception {
		String lead = done();
		TestUser superAdmin = survey.user("Eligibility Super Admin", "SUPER_ADMIN");

		assertFalse(assignedFlag(superAdmin, lead));
	}

	@Test
	void access_customerOrListingReviewer_forbidden() throws Exception {
		String lead = done();
		TestUser customer = survey.user("Eligibility Customer", "CUSTOMER");
		TestUser reviewer = survey.user("Eligibility Reviewer", "LISTING_REVIEWER");

		for (TestUser caller : List.of(customer, reviewer)) {
			Result answer = api.get(caller, path(lead));
			assertEquals(403, answer.status(), answer.rawBody());
			assertEquals("FORBIDDEN", answer.body().path("code").asString(), answer.rawBody());
		}
	}

	@Test
	void access_noToken_unauthorized() throws Exception {
		String lead = done();

		Result answer = api.get(null, path(lead));

		assertEquals(401, answer.status(), answer.rawBody());
		assertEquals("UNAUTHORIZED", answer.body().path("code").asString(), answer.rawBody());
	}

	@Test
	void access_malformedId_notFound() throws Exception {
		done();

		assertNotFound(api.get(admin, path("not-a-ulid")));
	}
}
