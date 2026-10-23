package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceCheck;
import vn.sunvibe.acquisition.core.application.usecase.survey.MeetingPlaceViolation;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadAccess;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadCard;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadEvent;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadFacts;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadHistoryEntry;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMove;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveRefusedException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadMoveResult;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadPlace;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadTarget;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadUseCase;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadVersionConflictException;
import vn.sunvibe.acquisition.core.application.usecase.survey.SurveyLeadViewer;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadFacts;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMove;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveEvent;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveRefusedException;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveResult;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadVersionConflictException;
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
