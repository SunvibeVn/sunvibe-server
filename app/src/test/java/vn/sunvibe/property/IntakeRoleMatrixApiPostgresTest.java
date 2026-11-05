package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/** The roles of property-create intake spec 4.3 on every path, and the trail of I-4 (S-28). Needs Docker. */
class IntakeRoleMatrixApiPostgresTest extends IntakeApiTestBase {

	private static final String ID = "01AAAAAAAAAAAAAAAAAAAAAAAA";

	@Override
	String phonePrefix() {
		return "0970";
	}

	private static Map<String, Object> version() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", 1);
		return body;
	}

	/** S-28: no staff role, no staff path, whatever the body says. */
	@Test
	void roles_refusedOnEveryStaffPath() throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		String lead = passed.leadId();
		for (String role : List.of("TENANT", "CUSTOMER", "OWNER", "LISTING_REVIEWER")) {
			TestUser caller = survey.user("Role " + role, role);
			assertEquals(403, block(caller, lead).status(), role);
			assertEquals(403, select(caller, lead, passed.spaceIds(), null).status(), role);
			assertEquals(403, requestOwner(caller, lead, "x@example.com", null).status(), role);
			assertEquals(403, search(caller, lead, "q=abc").status(), role);
			assertEquals(403, api.put(caller, base(lead) + "/target", Map.of("mode", "CREATE")).status(), role);
			assertEquals(403, api.put(caller, base(lead) + "/availability", Map.of("items", List.of(availabilityItem("s", "VACANT",
					today(), "A note")))).status(), role);
			assertEquals(403, preview(caller, lead).status(), role);
			assertEquals(403, complete(caller, lead, 1L, "x").status(), role);
			assertEquals(403, api.post(caller, base(lead) + "/restoration", version()).status(), role);
			assertEquals(403, context(caller, lead).status(), role);
			assertEquals(403, startCheck(caller, lead, ID, "A note", 1L).status(), role);
			assertEquals(403, refuse(caller, lead, ID, "A note", 1L).status(), role);
			assertEquals(403, suspend(caller, lead, null, "A reason", 1L).status(), role);
			assertEquals(403, release(caller, lead, ID, "A reason", 1L).status(), role);
			assertEquals(403, api.get(caller, CASES).status(), role);
			assertEquals(403, api.get(caller, CASES + "/" + ID).status(), role);
			assertEquals(403, api.post(caller, CASES + "/" + ID + "/decision", version()).status(), role);
		}
	}

	/** spec 4.3: the lead's assignee; a manager role alone does not. */
	@Test
	void managerNotAssignee_refusedOnAssigneeCommands() throws Exception {
		Passed passed = house(unique("Lê Duẩn"));
		String lead = passed.leadId();
		for (TestUser caller : List.of(manager, admin, leader)) {
			assertRefused(403, "INTAKE_NOT_ASSIGNEE", select(caller, lead, passed.spaceIds(), null));
			assertRefused(403, "INTAKE_NOT_ASSIGNEE", requestOwner(caller, lead, "x@example.com", null));
			assertRefused(403, "INTAKE_NOT_ASSIGNEE", search(caller, lead, "q=abc"));
			assertRefused(403, "INTAKE_NOT_ASSIGNEE", api.put(caller, base(lead) + "/target", Map.of("mode", "CREATE")));
			assertRefused(403, "INTAKE_NOT_ASSIGNEE", api.put(caller, base(lead) + "/availability",
					Map.of("items", List.of(availabilityItem(passed.spaceIds().getFirst(), "VACANT", today(), "A note")))));
			assertRefused(403, "INTAKE_NOT_ASSIGNEE", preview(caller, lead));
			assertRefused(403, "INTAKE_NOT_ASSIGNEE", complete(caller, lead, 1L, "x"));
			assertRefused(403, "INTAKE_NOT_ASSIGNEE", api.post(caller, base(lead) + "/restoration", version()));
		}
	}

	@Test
	void otherSale_404_onEveryLeadPath() throws Exception {
		Passed passed = house(unique("Hùng Vương"));

		assertEquals(404, block(otherSale, passed.leadId()).status());
		assertEquals(404, select(otherSale, passed.leadId(), passed.spaceIds(), null).status());
		assertEquals(404, preview(otherSale, passed.leadId()).status());
	}

	/** I-4: each assignee command writes one row, and a manager's commands make nobody an actor. */
	@Test
	void event_writtenByEveryAssigneeCommand() throws Exception {
		Passed passed = house(unique("Pasteur"));
		TestUser owner = owner();
		Issued issued = checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		assertEquals(204, withdraw(owner, issued.requestId(), passwordOf(owner)).status());
		Issued again = issued(passed.leadId(), address(owner));
		confirmed(owner, again);
		assertEquals(204, enter(owner, again.requestId(), started(admin, passed.leadId())).status());
		Map<String, Object> restoration = new LinkedHashMap<>();
		restoration.put("expectedVersion", versionOf(sale, passed.leadId()));
		Result restored = api.post(sale, base(passed.leadId()) + "/restoration", restoration);
		assertEquals(200, restored.status(), restored.rawBody());

		Object intake = intakeRow(passed.leadId()).get("id");
		Set<String> byAssignee = new TreeSet<>(jdbc.queryForList("select action from inventory_intake_events where intake_id = ?"
				+ " and actor_user_id = ?", String.class, intake, sale.userId()));

		assertEquals(Set.of("AVAILABILITY", "COMPLETION", "OWNER_REQUEST", "RESTORATION", "SELECTION", "TARGET"), byAssignee);
		assertEquals(0, count("select count(*) from inventory_intake_events where intake_id = ? and actor_user_id in (?, ?)", intake,
				manager.userId(), admin.userId()));
	}
}
