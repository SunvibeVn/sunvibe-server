package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountContext;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerLookup;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;

/**
 * The adapter only forwards brokerage's owner question to auth-service's owner standing (E05-S1 spec 5.5, S1-D4,
 * ADR-009); the rule lives in auth's {@code OwnerAccountService}.
 */
class AuthOwnerAccountAdapterTest {

	private final List<String> calls = new ArrayList<>();
	private OwnerStanding standing = new OwnerStanding(true, 0);

	private final AuthOwnerAccountAdapter adapter = new AuthOwnerAccountAdapter(new OwnerAccountUseCase() {
		@Override
		public boolean isEligibleOwner(String userId) {
			throw new UnsupportedOperationException("brokerage reads the standing");
		}

		@Override
		public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
			throw new UnsupportedOperationException("brokerage writes nothing");
		}

		@Override
		public OwnerLookup lookupConfirmable(String email) {
			throw new UnsupportedOperationException("not a brokerage question");
		}

		@Override
		public OwnerAccountContext accountContext(String userId) {
			throw new UnsupportedOperationException("not a brokerage question");
		}

		@Override
		public OwnerStanding ownerStanding(String userId) {
			calls.add(userId);
			return standing;
		}

		@Override
		public void notifyOwner(String userId, OwnerNoticeKind kind) {
			throw new UnsupportedOperationException("brokerage writes nothing");
		}
	});

	@Test
	void eligibleOwner_forwardsTheStanding() {
		assertTrue(adapter.eligibleOwner("01OWNER"));

		standing = new OwnerStanding(false, 2);
		assertFalse(adapter.eligibleOwner("01OTHER"));

		assertEquals(List.of("01OWNER", "01OTHER"), calls);
	}
}
