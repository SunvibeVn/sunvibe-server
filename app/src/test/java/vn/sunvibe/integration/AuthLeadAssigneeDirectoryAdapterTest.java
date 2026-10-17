package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The adapter only forwards acquisition's assignee question to auth-service (ADR-009, Q9-16); the rule lives in
 * auth's {@code LeadAssigneeAccountService}.
 */
class AuthLeadAssigneeDirectoryAdapterTest {

	private final List<String> calls = new ArrayList<>();
	private final AuthLeadAssigneeDirectoryAdapter adapter = new AuthLeadAssigneeDirectoryAdapter(
			userId -> {
				calls.add(userId);
				return "01ELIGIBLE0000000000000000".equals(userId);
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
}
