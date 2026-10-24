package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceCheck;
import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceViolation;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistActivity;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistActivityResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistEvent;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyChecklistHistoryEntry;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadCard;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadEvent;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadFacts;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadHistoryEntry;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMove;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveRefusedException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadOwnerForm;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadPlace;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadTarget;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadVersionConflictException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivity;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityResult;
import vn.sunvibe.survey.core.application.shared.external.lead.ChecklistActivityType;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveEvent;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveRefusedException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
import vn.sunvibe.survey.core.application.shared.external.lead.OwnerFormFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.PlaceCheck;
import vn.sunvibe.survey.core.application.shared.external.lead.SurveyViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.ViewerAccess;

/**
 * The adapter only forwards survey's lead questions to acquisition's published use case and translates the records
 * and the two published exceptions (survey-schedule spec 5.4, ADR-009); every rule stays in acquisition.
 */
class AcquisitionSurveyLeadAdapterTest {

	private static final String LEAD = "01JLEADAAAAAAAAAAAAAAAAAAA";
	private static final String SALE = "01JSALEAAAAAAAAAAAAAAAAAAA";
	private static final Instant AT = Instant.parse("2026-10-23T03:00:00Z");

	private final List<Object> calls = new ArrayList<>();
	private RuntimeException failure;

	private final AcquisitionSurveyLeadAdapter adapter = new AcquisitionSurveyLeadAdapter(new SurveyLeadUseCase() {
		@Override
		public SurveyLeadViewer viewerFor(String userId, Set<String> expandedAuthorities) {
			calls.add(expandedAuthorities);
			return new SurveyLeadViewer(userId, expandedAuthorities.contains("ROLE_SALE") ? SurveyLeadAccess.OWN
					: SurveyLeadAccess.ALL);
		}

		@Override
		public Optional<SurveyLeadFacts> findVisible(String leadId, SurveyLeadViewer viewer) {
			calls.add(viewer);
			return Optional.of(new SurveyLeadFacts(leadId, "SV-AAAA-BBBB", "ON_HOLD", "LONG_TERM_RENT", 2, SALE, 7L,
					AT, "Owner", new SurveyLeadPlace("12 Bach Dang", "48", "Da Nang", "20194", "Hai Chau",
							new BigDecimal("16.054407"), new BigDecimal("108.202167")), AT.minusSeconds(60),
					AT.minusSeconds(30)));
		}

		@Override
		public SurveyLeadMoveResult applySurveyMove(String leadId, long expectedVersion, SurveyLeadMove move) {
			calls.add(move);
			if (failure != null) {
				throw failure;
			}
			return new SurveyLeadMoveResult("SURVEY_SCHEDULED", expectedVersion + 1, AT,
					new SurveyLeadHistoryEntry("SURVEY_BOOKED", SALE, "Sale A", "CONTACTED", "SURVEY_SCHEDULED",
							move.referenceId(), move.referenceNo(), AT));
		}

		@Override
		public Map<String, SurveyLeadCard> findCards(SurveyLeadViewer viewer, Collection<String> leadIds) {
			calls.add(leadIds);
			return Map.of(LEAD, new SurveyLeadCard(LEAD, "SV-AAAA-BBBB", "Owner", "Hai Chau"));
		}

		@Override
		public MeetingPlaceCheck checkMeetingPlace(String provinceCode, String wardCode, BigDecimal latitude,
				BigDecimal longitude) {
			return "00000".equals(wardCode)
					? new MeetingPlaceCheck(null, null, null, null,
							List.of(new MeetingPlaceViolation("wardCode", "Ward is not in the catalog.")))
					: new MeetingPlaceCheck("Da Nang", "Hai Chau", latitude, longitude, List.of());
		}

		@Override
		public Optional<SurveyLeadFacts> findVisibleForShare(String leadId, SurveyLeadViewer viewer) {
			calls.add("share:" + leadId);
			return findVisible(leadId, viewer);
		}

		@Override
		public SurveyChecklistActivityResult recordChecklistActivity(String leadId, SurveyChecklistActivity activity) {
			calls.add(activity);
			return new SurveyChecklistActivityResult(AT, new SurveyChecklistHistoryEntry(activity.event().name(),
					activity.actorUserId(), "Sale A", activity.checklistId(), activity.checklistNo(),
					activity.detailOrNull(), activity.occurredAt()));
		}

		@Override
		public boolean leadExists(String leadId) {
			calls.add("exists:" + leadId);
			return LEAD.equals(leadId);
		}

		@Override
		public Optional<SurveyLeadOwnerForm> ownerForm(String leadId) {
			calls.add("form:" + leadId);
			return LEAD.equals(leadId)
					? Optional.of(new SurveyLeadOwnerForm(AT.minusSeconds(3600), "12 Bach Dang", "48", "20194",
							new BigDecimal("16.054407"), new BigDecimal("108.202167"), 15_000_000L,
							LocalDate.parse("2026-11-01")))
					: Optional.empty();
		}

		@Override
		public Set<String> leadStaffIds(String leadId) {
			calls.add("staff:" + leadId);
			return Set.of(SALE);
		}
	});

	@Test
	void viewer_mapsTheAccess() {
		assertEquals(new SurveyViewer(SALE, ViewerAccess.OWN), adapter.viewer(SALE, Set.of("ROLE_SALE")));
		assertEquals(ViewerAccess.ALL, adapter.viewer(SALE, Set.of("ROLE_ADMIN")).access());
	}

	@Test
	void findVisible_mapsEveryFact() {
		LeadFacts facts = adapter.findVisible(LEAD, new SurveyViewer(SALE, ViewerAccess.OWN)).orElseThrow();

		assertEquals(new SurveyLeadViewer(SALE, SurveyLeadAccess.OWN), calls.get(0));
		assertEquals(LEAD, facts.leadId());
		assertEquals("SV-AAAA-BBBB", facts.requestCode());
		assertEquals("ON_HOLD", facts.status());
		assertEquals("LONG_TERM_RENT", facts.intent());
		assertEquals(2, facts.intentCycle());
		assertEquals(SALE, facts.assignedUserId());
		assertEquals(7L, facts.version());
		assertEquals(AT, facts.lastActivityAt());
		assertEquals("Owner", facts.contactName());
		assertEquals("Hai Chau", facts.place().wardName());
		assertEquals(new BigDecimal("16.054407"), facts.place().latitude());
		assertEquals(AT.minusSeconds(60), facts.createdAt());
		assertEquals(AT.minusSeconds(30), facts.onHoldSince());
	}

	@Test
	void apply_forwardsTheMoveAndMapsTheResult() {
		LeadMoveResult result = adapter.apply(LEAD, 3L, new LeadMove(LeadMoveEvent.SURVEY_BOOKED,
				LeadMoveTarget.SURVEY_SCHEDULED, SALE, "01JAPPOINTMENTAAAAAAAAAAAA", 1, AT));

		assertEquals(new SurveyLeadMove(SurveyLeadEvent.SURVEY_BOOKED, SurveyLeadTarget.SURVEY_SCHEDULED, SALE,
				"01JAPPOINTMENTAAAAAAAAAAAA", 1, AT), calls.get(0));
		assertEquals("SURVEY_SCHEDULED", result.status());
		assertEquals(4L, result.version());
		assertEquals("Sale A", result.entry().actorName());
		assertEquals("CONTACTED", result.entry().fromStatus());
		assertEquals(1, result.entry().referenceNo());
	}

	@Test
	void apply_translatesThePublishedExceptions() {
		LeadMove move = new LeadMove(LeadMoveEvent.SURVEY_CANCELLED, LeadMoveTarget.KEEP, SALE, "01JAPPT", 1, AT);

		failure = new SurveyLeadVersionConflictException();
		assertThrows(LeadVersionConflictException.class, () -> adapter.apply(LEAD, 3L, move));

		failure = new SurveyLeadMoveRefusedException("ON_HOLD");
		assertEquals("ON_HOLD", assertThrows(LeadMoveRefusedException.class,
				() -> adapter.apply(LEAD, 3L, move)).getStatus());
	}

	@Test
	void everyEventAndTarget_hasItsCounterpart() {
		for (LeadMoveEvent event : LeadMoveEvent.values()) {
			assertEquals(event.name(), SurveyLeadEvent.valueOf(event.name()).name());
		}
		for (LeadMoveTarget target : LeadMoveTarget.values()) {
			assertEquals(target.name(), SurveyLeadTarget.valueOf(target.name()).name());
		}
		assertEquals(LeadMoveEvent.values().length, SurveyLeadEvent.values().length);
	}

	// ==================== checklist support (survey-checklist spec 5.4) ====================

	@Test
	void findVisibleForShare_forwardsToTheShareLockedRead() {
		LeadFacts facts = adapter.findVisibleForShare(LEAD, new SurveyViewer(SALE, ViewerAccess.ALL)).orElseThrow();

		assertEquals("share:" + LEAD, calls.get(0));
		assertEquals(new SurveyLeadViewer(SALE, SurveyLeadAccess.ALL), calls.get(1));
		assertEquals(7L, facts.version());
		assertEquals(AT.minusSeconds(30), facts.onHoldSince());
	}

	@Test
	void recordChecklistActivity_forwardsAndMapsTheEntry() {
		ChecklistActivityResult result = adapter.recordChecklistActivity(LEAD, new ChecklistActivity(
				ChecklistActivityType.CHECKLIST_SAVED, SALE, "01JCHECKLISTAAAAAAAAAAAAAA", 3, "TERMS", AT));

		assertEquals(new SurveyChecklistActivity(SurveyChecklistEvent.CHECKLIST_SAVED, SALE,
				"01JCHECKLISTAAAAAAAAAAAAAA", 3, "TERMS", AT), calls.get(0));
		assertEquals(AT, result.lastActivityAt());
		assertEquals("CHECKLIST_SAVED", result.entry().type());
		assertEquals(SALE, result.entry().actorUserId());
		assertEquals("Sale A", result.entry().actorName());
		assertEquals("01JCHECKLISTAAAAAAAAAAAAAA", result.entry().referenceId());
		assertEquals(3, result.entry().referenceNo());
		assertEquals("TERMS", result.entry().referenceDetail());
		assertEquals(AT, result.entry().occurredAt());
	}

	@Test
	void everyChecklistActivityType_hasItsCounterpart() {
		for (ChecklistActivityType type : ChecklistActivityType.values()) {
			assertEquals(type.name(), SurveyChecklistEvent.valueOf(type.name()).name());
		}
		assertEquals(ChecklistActivityType.values().length, SurveyChecklistEvent.values().length);
	}

	@Test
	void leadExistsAndLeadStaff_areForwarded() {
		assertTrue(adapter.leadExists(LEAD));
		assertEquals(false, adapter.leadExists("01JUNKNOWNAAAAAAAAAAAAAAAA"));
		assertEquals(Set.of(SALE), adapter.leadStaffIds(LEAD));
		assertEquals(List.of("exists:" + LEAD, "exists:01JUNKNOWNAAAAAAAAAAAAAAAA", "staff:" + LEAD), calls);
	}

	@Test
	void ownerForm_mapsEveryField() {
		OwnerFormFacts form = adapter.ownerForm(LEAD).orElseThrow();

		assertEquals(AT.minusSeconds(3600), form.submittedAt());
		assertEquals("12 Bach Dang", form.addressDetail());
		assertEquals("48", form.provinceCode());
		assertEquals("20194", form.wardCode());
		assertEquals(new BigDecimal("16.054407"), form.latitude());
		assertEquals(new BigDecimal("108.202167"), form.longitude());
		assertEquals(15_000_000L, form.monthlyAskingPriceOrNull());
		assertEquals(LocalDate.parse("2026-11-01"), form.availableFromOrNull());
	}

	@Test
	void ownerForm_emptyStaysEmpty() {
		assertEquals(Optional.empty(), adapter.ownerForm("01JUNKNOWNAAAAAAAAAAAAAAAA"));
	}

	@Test
	void cardsAndPlaceCheck_areMapped() {
		assertEquals("SV-AAAA-BBBB", adapter.cards(new SurveyViewer(SALE, ViewerAccess.ALL), List.of(LEAD)).get(LEAD)
				.requestCode());

		PlaceCheck ok = adapter.checkPlace("48", "20194", new BigDecimal("16.05"), new BigDecimal("108.2"));
		PlaceCheck broken = adapter.checkPlace("48", "00000", null, null);

		assertEquals("Hai Chau", ok.wardName());
		assertTrue(ok.violations().isEmpty());
		assertEquals("wardCode", broken.violations().get(0).field());
	}
}
