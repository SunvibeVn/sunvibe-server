package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountContext;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerLookup;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;

/**
 * The adapter only forwards brokerage's owner notice to auth-service's published owner notices with the kind
 * AGREEMENT_PENDING (brokerage-agreement BR-17; E05-S4 spec S4-D7, SD-13; ADR-009); the address, the text and the
 * delivery are auth's.
 */
class AuthOwnerNoticeAdapterTest {

	private final List<String> calls = new ArrayList<>();

	private final AuthOwnerNoticeAdapter adapter = new AuthOwnerNoticeAdapter(new OwnerAccountUseCase() {
		@Override
		public boolean isEligibleOwner(String userId) {
			throw new UnsupportedOperationException("not a notice");
		}

		@Override
		public boolean ensureOwnerRole(String ownerUserId, String actorUserId) {
			throw new UnsupportedOperationException("not a notice");
		}

		@Override
		public OwnerLookup lookupConfirmable(String email) {
			throw new UnsupportedOperationException("not a notice");
		}

		@Override
		public OwnerAccountContext accountContext(String userId) {
			throw new UnsupportedOperationException("not a notice");
		}

		@Override
		public OwnerStanding ownerStanding(String userId) {
			throw new UnsupportedOperationException("not a notice");
		}

		@Override
		public void notifyOwner(String userId, OwnerNoticeKind kind) {
			calls.add(userId + " " + kind);
		}
	});

	@Test
	void agreementPending_forwardsTheUserIdAndKind() {
		adapter.agreementPending("01OWNER");

		assertEquals(List.of("01OWNER AGREEMENT_PENDING"), calls);
	}

	/** E05-S6 spec S6-D13, NT-1: the activation notice forwards the owner with its own kind. */
	@Test
	void agreementActivated_notifiesWithItsKind() {
		adapter.agreementActivated("01OWNER");

		assertEquals(List.of("01OWNER AGREEMENT_ACTIVATED"), calls);
	}
}
