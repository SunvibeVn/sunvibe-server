package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;
import vn.sunvibe.property.core.application.shared.external.user.OwnerStandingFacts;

/** The adapter only translates auth's owner standing of a set of users into property-service's terms (E05-S6b read 4). */
class AuthOwnerStandingAdapterTest {

	private static AuthOwnerStandingAdapter answering(Map<String, OwnerStanding> answer) {
		return new AuthOwnerStandingAdapter(userIds -> answer);
	}

	@Test
	void ownerStandingsAdapter_mapsEveryField() {
		assertEquals(Map.of("01USER", new OwnerStandingFacts(true, 3)),
				answering(Map.of("01USER", new OwnerStanding(true, 3))).ownerStandings(List.of("01USER")));
	}

	@Test
	void ownerStandingsAdapter_emptyAnswer_empty() {
		assertEquals(Map.of(), answering(Map.of()).ownerStandings(List.of()));
	}

	@Test
	void ownerStandingsAdapter_unknownUser_keptAsNotEligible() {
		assertEquals(Map.of("01UNKNOWN", new OwnerStandingFacts(false, -1)),
				answering(Map.of("01UNKNOWN", new OwnerStanding(false, -1))).ownerStandings(List.of("01UNKNOWN")));
	}
}
