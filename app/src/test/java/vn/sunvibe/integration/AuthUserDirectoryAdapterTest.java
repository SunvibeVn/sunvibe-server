package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountContext;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerLookup;
import vn.sunvibe.auth.core.application.usecase.role.OwnerNoticeKind;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;
import vn.sunvibe.auth.core.application.usecase.user.PasswordReauthenticationUseCase;
import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult;
import vn.sunvibe.property.core.application.shared.external.user.OwnerAccountFacts;
import vn.sunvibe.property.core.application.shared.external.user.OwnerNotice;
import vn.sunvibe.property.core.application.shared.external.user.OwnerRoute;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingFacts;
import vn.sunvibe.property.core.application.shared.external.user.PasswordCheck;

/**
 * The adapter only forwards property-service's owner questions to auth-service (W6-7, W6-8; property-create intake spec
 * 5.1); the rules live in auth's {@code OwnerAccountService} and {@code PasswordReauthenticationService}.
 */
class AuthUserDirectoryAdapterTest {

	private static final String OWNER = "01OWNER0000000000000000000";

	private final List<String> calls = new ArrayList<>();
	private ReauthenticationResult reauthentication = ReauthenticationResult.ok(4);

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

		@Override
		public OwnerLookup lookupConfirmable(String email) {
			return switch (email) {
				case "found@example.com" -> OwnerLookup.found(OWNER);
				case "bad" -> OwnerLookup.invalid();
				default -> OwnerLookup.notFound();
			};
		}

		@Override
		public OwnerAccountContext accountContext(String userId) {
			return new OwnerAccountContext("o***@example.com", 12);
		}

		@Override
		public OwnerStanding ownerStanding(String userId) {
			return new OwnerStanding(true, 3);
		}

		@Override
		public void notifyOwner(String userId, OwnerNoticeKind kind) {
			calls.add("notify:" + userId + " " + kind);
		}
	}, new PasswordReauthenticationUseCase() {
		@Override
		public ReauthenticationResult check(String userId, String password) {
			calls.add("password:" + userId);
			return reauthentication;
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

	@Test
	void adapter_mapsEveryField() {
		assertEquals(new OwnerRoute(OwnerRoute.Outcome.FOUND, OWNER), adapter.lookupConfirmable("found@example.com"));
		assertEquals(new OwnerRoute(OwnerRoute.Outcome.NOT_FOUND, null), adapter.lookupConfirmable("x@example.com"));
		assertEquals(new OwnerRoute(OwnerRoute.Outcome.INVALID, null), adapter.lookupConfirmable("bad"));
		assertEquals(new OwnerAccountFacts("o***@example.com", 12), adapter.accountContext(OWNER));
		assertEquals(new OwnerStandingFacts(true, 3), adapter.ownerStanding(OWNER));
	}

	@Test
	void notify_mapsEveryKind() {
		for (OwnerNotice notice : OwnerNotice.values()) {
			adapter.notifyOwner(OWNER, notice);
		}

		assertEquals(List.of("notify:" + OWNER + " OWNER_CONFIRMED", "notify:" + OWNER + " OWNER_WITHDRAWN",
				"notify:" + OWNER + " OWNER_CHECK_REFUSED"), calls);
	}

	@Test
	void adapter_passwordOk_mapped() {
		PasswordCheck check = adapter.checkPassword(OWNER, "secret");

		assertEquals(PasswordCheck.Outcome.OK, check.outcome());
		assertNull(check.lockedForOrNull());
		assertEquals(List.of("password:" + OWNER), calls);
	}

	@Test
	void adapter_passwordWrong_mapped() {
		reauthentication = ReauthenticationResult.wrong();

		assertEquals(PasswordCheck.Outcome.WRONG, adapter.checkPassword(OWNER, "secret").outcome());
	}

	@Test
	void adapter_passwordLocked_mapped() {
		reauthentication = ReauthenticationResult.locked(Duration.ofMinutes(5));

		PasswordCheck check = adapter.checkPassword(OWNER, "secret");

		assertEquals(PasswordCheck.Outcome.LOCKED, check.outcome());
		assertEquals(Duration.ofMinutes(5), check.lockedForOrNull());
	}
}
