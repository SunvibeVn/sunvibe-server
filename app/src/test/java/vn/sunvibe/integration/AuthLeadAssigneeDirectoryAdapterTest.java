package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import vn.sunvibe.acquisition.core.application.shared.external.staff.EligibleAssignee;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccount;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;

/**
 * The adapter only forwards acquisition's assignee question to auth-service (ADR-009, Q9-16); the rule lives in
 * auth's {@code LeadAssigneeAccountService}.
 */
class AuthLeadAssigneeDirectoryAdapterTest {

	private final List<String> calls = new ArrayList<>();
	private final List<LeadAssigneeAccount> accounts = new ArrayList<>();
	private final List<Collection<String>> nameLookups = new ArrayList<>();
	private final AuthLeadAssigneeDirectoryAdapter adapter = new AuthLeadAssigneeDirectoryAdapter(
			new LeadAssigneeAccountUseCase() {
				@Override
				public boolean isEligibleOwnerLeadAssignee(String userId) {
					calls.add(userId);
					return "01ELIGIBLE0000000000000000".equals(userId);
				}

				@Override
				public List<LeadAssigneeAccount> listEligibleOwnerLeadAssignees() {
					return accounts;
				}

				@Override
				public Map<String, String> findStaffDisplayNames(Collection<String> userIds) {
					nameLookups.add(userIds);
					return Map.of("01SALE00000000000000000000", "Nguyen An");
				}
			});

	@Test
	void isEligibleAssignee_forwardsTheAnswer() {
		assertTrue(adapter.isEligibleAssignee("01ELIGIBLE0000000000000000"));
		assertFalse(adapter.isEligibleAssignee("01OTHER0000000000000000000"));
	}

	@Test
	void isEligibleAssignee_forwardsTheUserIdUnchanged() {
		adapter.isEligibleAssignee("01SALE00000000000000000000");

		assertEquals(List.of("01SALE00000000000000000000"), calls);
	}

	/** acquisition-owner-lead-queue-ui W11-9: records are mapped one to one; auth's order (here not by name) and role order are kept. */
	@Test
	void listForwardsAndMapsRecords() {
		accounts.add(new LeadAssigneeAccount("01B", "Binh", new LinkedHashSet<>(List.of("SALE", "SALE_LEADER"))));
		accounts.add(new LeadAssigneeAccount("01A", "An", Set.of("SALE_LEADER")));

		List<EligibleAssignee> listed = adapter.listEligibleAssignees();

		assertEquals(2, listed.size());
		assertEquals("01B", listed.get(0).userId());
		assertEquals("Binh", listed.get(0).fullName());
		assertEquals(List.of("SALE", "SALE_LEADER"), new ArrayList<>(listed.get(0).roles()));
		assertEquals("01A", listed.get(1).userId());
		assertEquals("An", listed.get(1).fullName());
		assertEquals(Set.of("SALE_LEADER"), listed.get(1).roles());
	}

	@Test
	void listIsEmptyWhenNobodyIsEligible() {
		assertEquals(List.of(), adapter.listEligibleAssignees());
	}

	/** W11-9: the ids go to auth unchanged and the names come back unchanged. */
	@Test
	void displayNamesForward() {
		Set<String> ids = Set.of("01SALE00000000000000000000", "01GONE00000000000000000000");

		Map<String, String> names = adapter.findDisplayNames(ids);

		assertEquals(Map.of("01SALE00000000000000000000", "Nguyen An"), names);
		assertEquals(1, nameLookups.size());
		assertEquals(ids, Set.copyOf(nameLookups.get(0)));
	}
}
