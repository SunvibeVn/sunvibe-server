package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeOriginFacts;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginView;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginView.UnitSpace;

/** The adapter only copies property-service's intake origin into brokerage's terms (E05-S3 spec S3-D17, ADR-009). */
class PropertyIntakeOriginAdapterTest {

	private final List<String> calls = new ArrayList<>();

	@Test
	void originOf_forwardsAndCopiesEveryField() {
		IntakeOriginView view = new IntakeOriginView("01LEAD", 3, "01REVISION", List.of(new UnitSpace("01UNIT2", "01SPACEB"),
				new UnitSpace("01UNIT1", "01SPACEA"), new UnitSpace("01UNIT2", "01SPACEC")));
		PropertyIntakeOriginAdapter adapter = new PropertyIntakeOriginAdapter(intakeId -> {
			calls.add(intakeId);
			return Optional.of(view);
		});

		IntakeOriginFacts facts = adapter.originOf("01INTAKE").orElseThrow();

		assertEquals("01LEAD", facts.leadId());
		assertEquals(3, facts.intentCycle());
		assertEquals("01REVISION", facts.sourceRevisionId());
		// in mapping order; a unit listed twice keeps its first space
		assertEquals(List.of(Map.entry("01UNIT2", "01SPACEB"), Map.entry("01UNIT1", "01SPACEA")),
				List.copyOf(facts.spaceIdByUnitId().entrySet()));
		assertThrows(UnsupportedOperationException.class, () -> facts.spaceIdByUnitId().put("01UNIT9", "01SPACEZ"));
		assertEquals(List.of("01INTAKE"), calls);
	}

	@Test
	void originOf_none_empty() {
		PropertyIntakeOriginAdapter adapter = new PropertyIntakeOriginAdapter(intakeId -> Optional.empty());

		assertTrue(adapter.originOf("01INTAKE").isEmpty());
	}
}
