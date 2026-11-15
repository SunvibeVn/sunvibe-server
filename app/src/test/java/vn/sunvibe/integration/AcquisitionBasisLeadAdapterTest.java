package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.brokerage.core.application.shared.external.lead.BasisLeadFacts;

/** The adapter only translates brokerage's lead question into acquisition's published use case (E05-S1 spec 5.5, ADR-009). */
class AcquisitionBasisLeadAdapterTest {

	private static final LeadIntakeFacts FACTS = new LeadIntakeFacts("01LEAD", "SVQ-1", "QUALIFIED", "LONG_TERM_RENT", 2,
			"01SALE", Set.of("01SALE", "01OLD"), "Hải Châu", false);

	private final List<String> calls = new ArrayList<>();
	private Optional<LeadIntakeFacts> visible = Optional.of(FACTS);

	private final AcquisitionBasisLeadAdapter adapter = new AcquisitionBasisLeadAdapter(new LeadIntakeUseCase() {
		@Override
		public SurveyLeadViewer viewerFor(String userId, Set<String> expandedAuthorities) {
			calls.add("viewerFor " + userId + " " + expandedAuthorities);
			return new SurveyLeadViewer("01SALE", SurveyLeadAccess.OWN);
		}

		@Override
		public Optional<LeadIntakeFacts> findVisible(String leadId, SurveyLeadViewer viewer) {
			calls.add("visible " + leadId + " " + viewer.userId() + " " + viewer.access());
			return visible;
		}

		@Override
		public Optional<LeadIntakeFacts> findVisibleForUpdate(String leadId, SurveyLeadViewer viewer) {
			calls.add("forUpdate " + leadId + " " + viewer.userId() + " " + viewer.access());
			return visible;
		}

		@Override
		public Optional<LeadIntakeFacts> lockForShare(String leadId) {
			throw new UnsupportedOperationException("brokerage never locks a lead");
		}

		@Override
		public Map<String, LeadIntakeFacts> systemFacts(Collection<String> leadIds) {
			throw new UnsupportedOperationException("brokerage reads a lead only through its viewer");
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
	void findVisible_forwardsViewerAndMapsFacts() {
		BasisLeadFacts lead = adapter.findVisible("01LEAD", "01SALE", Set.of("ROLE_SALE")).orElseThrow();

		assertEquals(new BasisLeadFacts("01LEAD", "01SALE"), lead);
		assertEquals(List.of("viewerFor 01SALE [ROLE_SALE]", "visible 01LEAD 01SALE OWN"), calls);
	}

	@Test
	void findVisible_notVisible_empty() {
		visible = Optional.empty();

		assertTrue(adapter.findVisible("01LEAD", "01OTHER", Set.of("ROLE_SALE")).isEmpty());
	}

	/** E05-S2 spec S2-D2, AD-14: the create command reads the lead under acquisition's row lock. */
	@Test
	void findVisibleForUpdate_forwardsViewerAndMapsFacts() {
		BasisLeadFacts lead = adapter.findVisibleForUpdate("01LEAD", "01SALE", Set.of("ROLE_SALE")).orElseThrow();

		assertEquals(new BasisLeadFacts("01LEAD", "01SALE"), lead);
		assertEquals(List.of("viewerFor 01SALE [ROLE_SALE]", "forUpdate 01LEAD 01SALE OWN"), calls);
	}

	@Test
	void findVisibleForUpdate_notVisible_empty() {
		visible = Optional.empty();

		assertTrue(adapter.findVisibleForUpdate("01LEAD", "01OTHER", Set.of("ROLE_SALE")).isEmpty());
	}
}
