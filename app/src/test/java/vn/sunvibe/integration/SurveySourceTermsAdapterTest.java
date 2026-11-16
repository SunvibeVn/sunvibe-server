package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.brokerage.core.application.shared.external.survey.SourceTermsFacts;
import vn.sunvibe.survey.core.application.usecase.checklist.IntakeSourceView;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyIntakeSourceUseCase;

/** The adapter only copies survey's current source terms into brokerage's terms (E05-S3 spec S3-D11, ADR-009). */
class SurveySourceTermsAdapterTest {

	private static final LocalDate VISIT = LocalDate.of(2026, 11, 9);
	private static final LocalDate STATED = LocalDate.of(2026, 11, 10);

	private final List<String> calls = new ArrayList<>();
	private final IntakeSourceView view = new IntakeSourceView("01REV", Instant.parse("2026-11-10T03:00:00Z"), "01PERF",
			"01REC", "a".repeat(64), "PARTS", "WHOLE_BUILDING", "12 Bạch Đằng", "48", "Đà Nẵng", "20242", "Hải Châu",
			new BigDecimal("16.0678"), new BigDecimal("108.2208"), VISIT,
			List.of(space("01SPACEA", "P101"), space("01SPACEB", "P102")),
			List.of(new IntakeSourceView.Term("monthlyRent", "P101", 8_000_000L, "VND", "MONTH", null, "OWNER_STATED", STATED),
					new IntakeSourceView.Term("otherFees:Cleaning", null, 100_000L, "VND", "PER_MONTH", null,
							"OBSERVED_ON_SITE", null),
					new IntakeSourceView.Term("deposit", "P101", null, null, null, "2 months", null, null)),
			true, null, List.of());

	private final SurveySourceTermsAdapter adapter = new SurveySourceTermsAdapter(new SurveyIntakeSourceUseCase() {
		@Override
		public Optional<IntakeSourceView> currentSource(String leadId, int intentCycle) {
			calls.add(leadId + " " + intentCycle);
			return "01LEAD".equals(leadId) && intentCycle == 2 ? Optional.of(view) : Optional.empty();
		}

		@Override
		public List<IntakeSourceView.MarkedRevision> markedRevisions(String leadId) {
			throw new AssertionError("not read by the adapter");
		}
	});

	@Test
	void currentTerms_forwardsAndCopiesEveryField() {
		SourceTermsFacts facts = adapter.currentTerms("01LEAD", 2).orElseThrow();

		assertEquals(List.of("01LEAD 2"), calls);
		assertEquals("01REV", facts.revisionId());
		assertEquals(VISIT, facts.sourceDate());
		assertEquals(List.of(new SourceTermsFacts.Space("01SPACEA", "P101"), new SourceTermsFacts.Space("01SPACEB", "P102")),
				facts.spaces());
		assertEquals(List.of(
				new SourceTermsFacts.Term("monthlyRent", "P101", 8_000_000L, "VND", "MONTH", null, "OWNER_STATED", STATED),
				new SourceTermsFacts.Term("otherFees:Cleaning", null, 100_000L, "VND", "PER_MONTH", null, "OBSERVED_ON_SITE",
						null),
				new SourceTermsFacts.Term("deposit", "P101", null, null, null, "2 months", null, null)), facts.terms());
	}

	@Test
	void currentTerms_none_empty() {
		assertTrue(adapter.currentTerms("01OTHER", 2).isEmpty());
		assertTrue(adapter.currentTerms("01LEAD", 1).isEmpty());
	}

	/** brokerage-agreement BR-17, ED-17: no amount and no fee name in a toString. */
	@Test
	void sourceTermsFacts_toString_holdsNoValue() {
		SourceTermsFacts facts = adapter.currentTerms("01LEAD", 2).orElseThrow();

		List<String> texts = new ArrayList<>(List.of(facts.toString()));
		facts.terms().forEach(term -> texts.add(term.toString()));
		for (String text : texts) {
			for (String value : List.of("8000000", "100000", "Cleaning", "2 months", "P101")) {
				assertFalse(text.contains(value), text);
			}
		}
	}

	private static IntakeSourceView.Space space(String spaceId, String code) {
		return new IntakeSourceView.Space(spaceId, code, "Floor 1", "ENTERED_ALL", VISIT, new BigDecimal("25.5"), 1, 1,
				"VACANT", List.of(), List.of());
	}
}
