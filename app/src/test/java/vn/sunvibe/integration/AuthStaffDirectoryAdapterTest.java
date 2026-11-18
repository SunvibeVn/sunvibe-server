package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccount;
import vn.sunvibe.auth.core.application.usecase.staff.LeadAssigneeAccountUseCase;

/**
 * The adapter answers brokerage's staff names of history rows (E05-S7 spec S7-D7, S7-D15, AD-1) with auth's published
 * directory, and only forwards; an empty question costs no lookup (ADR-009).
 */
class AuthStaffDirectoryAdapterTest {

	private static final String STAFF = "01JSTAFF000000000000000000";
	private static final String UNKNOWN = "01JUNKNOWN0000000000000000";

	private final List<Set<String>> calls = new ArrayList<>();

	private final AuthStaffDirectoryAdapter adapter = new AuthStaffDirectoryAdapter(new LeadAssigneeAccountUseCase() {
		@Override
		public boolean isEligibleOwnerLeadAssignee(String userId) {
			throw new UnsupportedOperationException("brokerage asks names only");
		}

		@Override
		public boolean canSignIn(String userId) {
			throw new UnsupportedOperationException("brokerage asks names only");
		}

		@Override
		public List<LeadAssigneeAccount> listEligibleOwnerLeadAssignees() {
			throw new UnsupportedOperationException("brokerage asks names only");
		}

		@Override
		public Map<String, String> findStaffDisplayNames(Collection<String> userIds) {
			calls.add(Set.copyOf(userIds));
			return Map.of(STAFF, "Tran Thi B");
		}
	});

	@Test
	void displayNames_forwardsTheIds() {
		assertEquals(Map.of(STAFF, "Tran Thi B"), adapter.displayNames(List.of(STAFF, UNKNOWN)));
		assertEquals(List.of(Set.of(STAFF, UNKNOWN)), calls);
	}

	@Test
	void displayNames_emptyInput_noCall() {
		assertEquals(Map.of(), adapter.displayNames(List.of()));
		assertEquals(List.of(), calls);
	}
}
