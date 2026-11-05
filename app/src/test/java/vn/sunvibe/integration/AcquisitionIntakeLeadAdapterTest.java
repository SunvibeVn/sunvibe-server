package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeActivity;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeEvent;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeFacts;
import vn.sunvibe.acquisition.core.application.usecase.inventory.LeadIntakeUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeHistoryEntry;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeLead;
import vn.sunvibe.property.core.application.shared.external.lead.IntakeViewer;

/** The adapter only translates between the two modules' terms (property-create intake spec 5.1, ADR-009). */
class AcquisitionIntakeLeadAdapterTest {

	private static final Instant AT = Instant.parse("2026-11-06T02:00:00Z");
	private static final LeadIntakeFacts FACTS = new LeadIntakeFacts("01LEAD", "SVQ-1", "QUALIFIED", "LONG_TERM_RENT", 2,
			"01SALE", Set.of("01SALE", "01OLD"), "Hải Châu", false);

	private final List<String> calls = new ArrayList<>();
	private final List<LeadIntakeActivity> activities = new ArrayList<>();

	private final AcquisitionIntakeLeadAdapter adapter = new AcquisitionIntakeLeadAdapter(new LeadIntakeUseCase() {
		@Override
		public SurveyLeadViewer viewerFor(String userId, Set<String> authorities) {
			return new SurveyLeadViewer(userId, authorities.contains("ROLE_ADMIN") ? SurveyLeadAccess.ALL
					: SurveyLeadAccess.OWN);
		}

		@Override
		public Optional<LeadIntakeFacts> findVisible(String leadId, SurveyLeadViewer viewer) {
			calls.add("visible " + leadId + " " + viewer.userId() + " " + viewer.access());
			return Optional.of(FACTS);
		}

		@Override
		public Optional<LeadIntakeFacts> findVisibleForUpdate(String leadId, SurveyLeadViewer viewer) {
			calls.add("update " + leadId + " " + viewer.access());
			return Optional.of(FACTS);
		}

		@Override
		public Optional<LeadIntakeFacts> lockForShare(String leadId) {
			calls.add("share " + leadId);
			return Optional.of(FACTS);
		}

		@Override
		public Map<String, LeadIntakeFacts> systemFacts(Collection<String> leadIds) {
			return Map.of("01LEAD", FACTS);
		}

		@Override
		public List<String> otherOpenLeadIdsOfPhone(String leadId, SurveyLeadViewer viewer) {
			return List.of("01OTHER:" + viewer.access());
		}

		@Override
		public void recordIntakeActivity(String leadId, LeadIntakeActivity activity) {
			activities.add(activity);
		}
	});

	@Test
	void adapter_mapsEveryField() {
		IntakeLead lead = adapter.findVisible("01LEAD", new IntakeViewer("01SALE", false)).orElseThrow();

		assertEquals(new IntakeLead("01LEAD", "SVQ-1", "QUALIFIED", "LONG_TERM_RENT", 2, "01SALE",
				Set.of("01SALE", "01OLD"), "Hải Châu", false), lead);
		assertEquals(List.of("visible 01LEAD 01SALE OWN"), calls);
	}

	@Test
	void viewer_mapsAccess() {
		assertTrue(adapter.viewerFor("01ADMIN", Set.of("ROLE_ADMIN")).seesAllLeads());
		assertFalse(adapter.viewerFor("01SALE", Set.of("ROLE_SALE")).seesAllLeads());
	}

	@Test
	void locks_forward() {
		adapter.findVisibleForUpdate("01LEAD", new IntakeViewer("01ADMIN", true));
		adapter.lockForShare("01LEAD");

		assertEquals(List.of("update 01LEAD ALL", "share 01LEAD"), calls);
	}

	@Test
	void systemFacts_mapped() {
		assertEquals("SVQ-1", adapter.systemFacts(List.of("01LEAD")).get("01LEAD").leadCode());
	}

	@Test
	void phoneLeads_forwardTheViewer() {
		assertEquals(List.of("01OTHER:ALL"), adapter.otherOpenLeadIdsOfPhone("01LEAD", new IntakeViewer("01M", true)));
	}

	@Test
	void activity_mapsEveryType() {
		for (IntakeHistoryEntry.Type type : IntakeHistoryEntry.Type.values()) {
			adapter.recordActivity("01LEAD", new IntakeHistoryEntry(type, null, "01INTAKE", 2, AT));
		}

		assertEquals(List.of(LeadIntakeEvent.INVENTORY_INTAKE_COMPLETED, LeadIntakeEvent.INVENTORY_INTAKE_SUSPENDED,
				LeadIntakeEvent.INVENTORY_INTAKE_RESTORED), activities.stream().map(LeadIntakeActivity::event).toList());
		assertEquals(new LeadIntakeActivity(LeadIntakeEvent.INVENTORY_INTAKE_COMPLETED, null, "01INTAKE", 2, AT),
				activities.getFirst());
	}
}
