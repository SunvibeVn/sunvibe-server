package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccount;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;

/**
 * The adapter forwards survey's surveyor questions to auth's published use case (survey-schedule spec 4.7, 5.4); the
 * eligibility rule stays in auth. An empty question costs no lookup.
 */
class AuthSurveyorDirectoryAdapterTest {

	private static final String ELIGIBLE = "01ELIGIBLE0000000000000000";
	private static final String OTHER = "01OTHER0000000000000000000";

	private final List<String> calls = new ArrayList<>();
	private final AuthSurveyorDirectoryAdapter adapter = new AuthSurveyorDirectoryAdapter(
			new LeadAssigneeAccountUseCase() {
				@Override
				public boolean isEligibleOwnerLeadAssignee(String userId) {
					calls.add("one:" + userId);
					return ELIGIBLE.equals(userId);
				}

				@Override
				public List<LeadAssigneeAccount> listEligibleOwnerLeadAssignees() {
					calls.add("list");
					return List.of(new LeadAssigneeAccount(ELIGIBLE, "Nguyen An", Set.of("SALE")),
							new LeadAssigneeAccount("01THIRD0000000000000000000", "Tran Binh", Set.of("SALE_LEADER")));
				}

				@Override
				public Map<String, String> findStaffDisplayNames(Collection<String> userIds) {
					calls.add("names");
					return Map.of(ELIGIBLE, "Nguyen An");
				}
			});

	@Test
	void isEligible_forwards() {
		assertTrue(adapter.isEligible(ELIGIBLE));
		assertFalse(adapter.isEligible(OTHER));
		assertFalse(adapter.isEligible(null));
	}

	/** Asked per id: auth's eligible list is cut at 500 accounts, so it cannot answer for every id. */
	@Test
	void eligibleAmong_asksAuthForEachId() {
		assertEquals(Set.of(ELIGIBLE), adapter.eligibleAmong(Set.of(ELIGIBLE, OTHER)));
		assertEquals(Set.of("one:" + ELIGIBLE, "one:" + OTHER), Set.copyOf(calls));
	}

	@Test
	void emptyQuestions_costNoLookup() {
		assertEquals(Set.of(), adapter.eligibleAmong(Set.of()));
		assertEquals(Map.of(), adapter.displayNames(Set.of()));
		assertEquals(List.of(), calls);
	}

	@Test
	void displayNames_forwards() {
		assertEquals(Map.of(ELIGIBLE, "Nguyen An"), adapter.displayNames(Set.of(ELIGIBLE, OTHER)));
	}
}
