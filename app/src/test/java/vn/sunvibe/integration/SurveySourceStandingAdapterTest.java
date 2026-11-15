package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.property.core.application.shared.external.survey.SourceStandingFacts;
import vn.sunvibe.survey.core.application.usecase.checklist.IntakeSourceView;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingView;

/** The adapter only translates survey's source standing of a set of leads into property-service's terms (E05-S6b read 2). */
class SurveySourceStandingAdapterTest {

	private static final Instant MARKED = Instant.parse("2026-11-05T04:00:00Z");

	private static SurveySourceStandingAdapter answering(Map<String, SourceStandingView> answer) {
		return new SurveySourceStandingAdapter(intentCycleByLead -> answer);
	}

	@Test
	void standingsAdapter_mapsEveryField() {
		SurveySourceStandingAdapter adapter = answering(Map.of("01LEAD", new SourceStandingView("01REV", false, "EVIDENCE_HELD",
				List.of(new IntakeSourceView.MarkedRevision("01OLD", "WRONG_SCOPE", MARKED)))));

		Map<String, SourceStandingFacts> facts = adapter.standings(Map.of("01LEAD", 2));

		assertEquals(Map.of("01LEAD", new SourceStandingFacts("01REV", false, "EVIDENCE_HELD",
				List.of(new IntakeSource.MarkedRevision("01OLD", "WRONG_SCOPE", MARKED)))), facts);
	}

	@Test
	void standingsAdapter_emptyAnswer_empty() {
		assertEquals(Map.of(), answering(Map.of()).standings(Map.of()));
	}

	@Test
	void standingsAdapter_unknownLead_keptAsNoSource() {
		SurveySourceStandingAdapter adapter = answering(Map.of("01UNKNOWN", new SourceStandingView(null, false, null,
				List.of())));

		assertEquals(Map.of("01UNKNOWN", new SourceStandingFacts(null, false, null, List.of())),
				adapter.standings(Map.of("01UNKNOWN", 1)));
	}
}
