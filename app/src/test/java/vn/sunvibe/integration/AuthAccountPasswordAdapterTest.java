package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.user.ReauthenticationResult;
import vn.sunvibe.brokerage.core.application.shared.external.account.PasswordCheck;

/** The adapter only maps auth's re-authentication outcome by name (E05-S5 spec S5-D4, ADR-009). */
class AuthAccountPasswordAdapterTest {

	private final List<String> calls = new ArrayList<>();

	@Test
	void check_eachOutcome_mappedByName() {
		List<ReauthenticationResult> results = List.of(ReauthenticationResult.ok(3), ReauthenticationResult.wrong(),
				ReauthenticationResult.locked(Duration.ofMinutes(15)));
		List<PasswordCheck.Outcome> expected = List.of(PasswordCheck.Outcome.OK, PasswordCheck.Outcome.WRONG,
				PasswordCheck.Outcome.LOCKED);

		for (int index = 0; index < results.size(); index++) {
			ReauthenticationResult result = results.get(index);
			AuthAccountPasswordAdapter adapter = new AuthAccountPasswordAdapter((userId, password) -> {
				calls.add(userId + " " + password);
				return result;
			});

			assertEquals(new PasswordCheck(expected.get(index)), adapter.check("01JOWNER", "secret-" + index));
		}
		assertEquals(List.of("01JOWNER secret-0", "01JOWNER secret-1", "01JOWNER secret-2"), calls);
	}
}
