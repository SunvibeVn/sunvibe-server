package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeActivity;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeFacts;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.brokerage.core.application.shared.external.lead.LeadAssigneeFacts;

/**
 * The adapter answers brokerage's assignees of a set of basis leads (group (ii), E05-S6 spec S6-D10, S6-D17) with
 * acquisition's viewer-less set read, and only translates; an unknown lead stays absent (ADR-009).
 */
class AcquisitionBasisLeadAssigneeAdapterTest {

	private final List<String> calls = new ArrayList<>();

	private final AcquisitionBasisLeadAssigneeAdapter adapter = new AcquisitionBasisLeadAssigneeAdapter(
			new LeadIntakeUseCase() {
				@Override
				public SurveyLeadViewer viewerFor(String userId, Set<String> expandedAuthorities) {
					throw new UnsupportedOperationException("the assignees are read with no viewer");
				}

				@Override
				public Optional<LeadIntakeFacts> findVisible(String leadId, SurveyLeadViewer viewer) {
					throw new UnsupportedOperationException("the assignees are read with no viewer");
				}

				@Override
				public Optional<LeadIntakeFacts> findVisibleForUpdate(String leadId, SurveyLeadViewer viewer) {
					throw new UnsupportedOperationException("the assignees are read with no viewer");
				}

				@Override
				public Optional<LeadIntakeFacts> lockForShare(String leadId) {
					throw new UnsupportedOperationException("the assignees lock nothing");
				}

				@Override
				public Map<String, LeadIntakeFacts> systemFacts(Collection<String> leadIds) {
					calls.add("systemFacts " + leadIds);
					return Map.of(
							"01LEADA", new LeadIntakeFacts("01LEADA", "SVQ-1", "QUALIFIED", "LONG_TERM_RENT", 1, "01SALE",
									Set.of("01SALE"), "Hải Châu", false),
							"01LEADB", new LeadIntakeFacts("01LEADB", "SVQ-2", "NEW", "LONG_TERM_RENT", 1, null, Set.of(),
									"Hải Châu", false));
				}

				@Override
				public List<String> otherOpenLeadIdsOfPhone(String leadId, SurveyLeadViewer viewer) {
					throw new UnsupportedOperationException("not a brokerage question");
				}

				@Override
				public void recordIntakeActivity(String leadId, LeadIntakeActivity activity) {
					throw new UnsupportedOperationException("brokerage writes nothing");
				}
			});

	@Test
	void assigneesOf_assigneeTranslatedUnknownAbsent() {
		List<LeadAssigneeFacts> facts = adapter.assigneesOf(List.of("01LEADA", "01LEADB", "01UNKNOWN"));

		assertEquals(Set.of(new LeadAssigneeFacts("01LEADA", "01SALE"), new LeadAssigneeFacts("01LEADB", null)),
				Set.copyOf(facts));
		assertEquals(2, facts.size());
		assertEquals(List.of("systemFacts [01LEADA, 01LEADB, 01UNKNOWN]"), calls);
	}
}
