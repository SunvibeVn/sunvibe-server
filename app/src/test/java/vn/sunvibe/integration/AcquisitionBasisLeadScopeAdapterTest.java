package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.application.usecase.StaffLeadScope;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadScopeFacts;

/**
 * The adapter answers brokerage's lead scope of the caller (E05-S7 spec S7-D2, AD-1) with acquisition's published
 * read, and only translates: the user and the authorities pass unchanged, the answer keeps its flag and its ids
 * (ADR-009).
 */
class AcquisitionBasisLeadScopeAdapterTest {

	private static final String SALE = "01JSALE0000000000000000000";

	private final List<String> calls = new ArrayList<>();

	private final AcquisitionBasisLeadScopeAdapter adapter = new AcquisitionBasisLeadScopeAdapter(
			(userId, expandedAuthorities) -> {
				calls.add(userId + " " + expandedAuthorities);
				return expandedAuthorities.contains("ROLE_SALE_LEADER") ? new StaffLeadScope(true, Set.of())
						: new StaffLeadScope(false, Set.of("01LEADA", "01LEADB"));
			});

	@Test
	void scopeOf_forwardsTheCallerAndTranslates() {
		LeadScopeFacts own = adapter.scopeOf(SALE, Set.of("ROLE_SALE"));
		LeadScopeFacts all = adapter.scopeOf(SALE, Set.of("ROLE_SALE_LEADER"));

		assertFalse(own.allLeads());
		assertEquals(Set.of("01LEADA", "01LEADB"), own.leadIds());
		assertTrue(all.allLeads());
		assertEquals(Set.of(), all.leadIds());
		assertEquals(List.of(SALE + " [ROLE_SALE]", SALE + " [ROLE_SALE_LEADER]"), calls);
	}
}
