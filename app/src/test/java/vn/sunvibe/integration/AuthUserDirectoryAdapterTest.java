package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;

/**
 * The adapter only forwards property-service's owner questions to auth-service (W6-7, W6-8); the rules live in
 * auth's {@code OwnerAccountService}.
 */
class AuthUserDirectoryAdapterTest {

	private final List<String> calls = new ArrayList<>();
	private final AuthUserDirectoryAdapter adapter = new AuthUserDirectoryAdapter(new OwnerAccountUseCase() {
		@Override
		public boolean isEligibleOwner(String userId) {
			calls.add("eligible?" + userId);
			return "01ELIGIBLE0000000000000000".equals(userId);
		}

		@Override
		public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
			calls.add("ensureOwner:" + ownerUserId + " by " + actorUserId);
			return true;
		}
	});

	@Test
	void isEligibleOwner_forwardsTheAnswer() {
		assertTrue(adapter.isEligibleOwner("01ELIGIBLE0000000000000000"));
		assertFalse(adapter.isEligibleOwner("01OTHER0000000000000000000"));
	}

	@Test
	void ensureOwnerRole_forwardsOwnerAndActor() {
		assertTrue(adapter.ensureOwnerRole("01OWNER0000000000000000000", "01ACTOR0000000000000000000"));

		assertEquals(List.of("ensureOwner:01OWNER0000000000000000000 by 01ACTOR0000000000000000000"), calls);
	}
}
