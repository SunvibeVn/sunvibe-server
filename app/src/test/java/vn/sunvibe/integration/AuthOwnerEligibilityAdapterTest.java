package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;

/**
 * The adapter answers brokerage's eligible owners of a set of accounts with auth-service's published owner standings,
 * and only translates (E05-S6 spec S6-D17; brokerage-agreement BR-4; ADR-009).
 */
class AuthOwnerEligibilityAdapterTest {

	private final List<String> calls = new ArrayList<>();

	private final AuthOwnerEligibilityAdapter adapter = new AuthOwnerEligibilityAdapter(userIds -> {
		calls.add("ownerStandings " + userIds);
		return Map.of("O1", new OwnerStanding(true, 0), "O2", new OwnerStanding(false, 3),
				"O3", new OwnerStanding(false, -1));
	});

	@Test
	void eligibleOwners_onlyEligibleStandings() {
		assertEquals(Set.of("O1"), adapter.eligibleOwners(List.of("O1", "O2", "O3")));
		assertEquals(List.of("ownerStandings [O1, O2, O3]"), calls);
	}
}
