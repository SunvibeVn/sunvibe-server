package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;
import vn.sunvibe.survey.core.application.usecase.checklist.IntakeSourceView;
import vn.sunvibe.survey.core.application.usecase.checklist.SurveyIntakeSourceUseCase;

/** The adapter only translates the survey's source into property-service's terms (property-create intake spec 5.1). */
class SurveyIntakeSourceAdapterTest {

	private static final Instant DECIDED = Instant.parse("2026-11-05T03:00:00Z");
	private static final LocalDate DAY = LocalDate.of(2026, 11, 4);
	private static final Instant MARKED = Instant.parse("2026-11-05T04:00:00Z");

	private final IntakeSourceView.Space space = new IntakeSourceView.Space("01SPACE", "P101", "Floor 1", "ENTERED_ALL",
			DAY, new BigDecimal("25.5"), 1, 2, "VACANT", List.of("BALCONY"),
			List.of(new IntakeSourceView.FieldSource("area", "MEASURED", DAY)));
	private final IntakeSourceView view = new IntakeSourceView("01REV", DECIDED, "01PERF", "01REC", "a".repeat(64), "PARTS",
			"WHOLE_BUILDING", "12 Bạch Đằng", "48", "Đà Nẵng", "20242", "Hải Châu", new BigDecimal("16.0678"),
			new BigDecimal("108.2208"), DAY, List.of(space),
			List.of(new IntakeSourceView.Term("monthlyRent", "P101", 5_000_000L, "VND", "MONTH", "text", "OWNER_STATED", DAY)),
			false, "EVIDENCE_HELD", List.of(new IntakeSourceView.MarkedRevision("01OLD", "WRONG_SCOPE", MARKED)));

	private final SurveyIntakeSourceAdapter adapter = new SurveyIntakeSourceAdapter(new SurveyIntakeSourceUseCase() {
		@Override
		public Optional<IntakeSourceView> currentSource(String leadId, int intentCycle) {
			return "01LEAD".equals(leadId) && intentCycle == 2 ? Optional.of(view) : Optional.empty();
		}

		@Override
		public List<IntakeSourceView.MarkedRevision> markedRevisions(String leadId) {
			return view.markedRevisions();
		}
	});

	@Test
	void adapter_mapsEveryField() {
		IntakeSource source = adapter.currentSource("01LEAD", 2).orElseThrow();

		assertEquals("01REV", source.revisionId());
		assertEquals(DECIDED, source.decidedAt());
		assertEquals("01PERF", source.performedBy());
		assertEquals("01REC", source.recordedBy());
		assertEquals("a".repeat(64), source.contentSha256());
		assertEquals("PARTS", source.rentalScope());
		assertEquals("WHOLE_BUILDING", source.assetTypeOrNull());
		assertEquals("12 Bạch Đằng", source.addressText());
		assertEquals("48", source.provinceCode());
		assertEquals("Đà Nẵng", source.provinceName());
		assertEquals("20242", source.wardCode());
		assertEquals("Hải Châu", source.wardName());
		assertEquals(new BigDecimal("16.0678"), source.latitude());
		assertEquals(new BigDecimal("108.2208"), source.longitude());
		assertEquals(DAY, source.sourceDate());
		assertEquals(false, source.usable());
		assertEquals("EVIDENCE_HELD", source.reasonOrNull());
		assertEquals(List.of(new IntakeSource.MarkedRevision("01OLD", "WRONG_SCOPE", MARKED)), source.markedRevisions());
		IntakeSource.Space mapped = source.spaces().getFirst();
		assertEquals(new IntakeSource.Space("01SPACE", "P101", "Floor 1", "ENTERED_ALL", DAY, new BigDecimal("25.5"), 1, 2,
				"VACANT", List.of("BALCONY"), List.of(new IntakeSource.FieldSource("area", "MEASURED", DAY))), mapped);
		assertEquals(new IntakeSource.Term("monthlyRent", "P101", 5_000_000L, "VND", "MONTH", "text", "OWNER_STATED", DAY),
				source.terms().getFirst());
	}

	@Test
	void adapter_noPassed_empty() {
		assertEquals(Optional.empty(), adapter.currentSource("01OTHER", 2));
	}

	@Test
	void adapter_markedRevisions_mapped() {
		assertEquals(List.of(new IntakeSource.MarkedRevision("01OLD", "WRONG_SCOPE", MARKED)), adapter.markedRevisions("01LEAD"));
	}
}
