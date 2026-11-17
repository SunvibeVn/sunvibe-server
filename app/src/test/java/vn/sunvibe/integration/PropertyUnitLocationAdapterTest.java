package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.brokerage.core.application.shared.external.property.UnitLocationFacts;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationView;

/** The adapter only copies property-service's unit locations into brokerage's terms (E05-S5 spec S5-D2, ADR-009). */
class PropertyUnitLocationAdapterTest {

	private final List<List<String>> calls = new ArrayList<>();

	@Test
	void locate_views_translatedFacts() {
		PropertyUnitLocationAdapter adapter = new PropertyUnitLocationAdapter(unitIds -> {
			calls.add(List.copyOf(unitIds));
			return List.of(new UnitLocationView("01UNIT1", "P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau"),
					new UnitLocationView("01UNIT2", null, null, null, null));
		});

		List<UnitLocationFacts> facts = adapter.locate(List.of("01UNIT1", "01UNIT2", "01UNIT3"));

		assertEquals(List.of(new UnitLocationFacts("01UNIT1", "P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau"),
				new UnitLocationFacts("01UNIT2", null, null, null, null)), facts);
		assertEquals(List.of(List.of("01UNIT1", "01UNIT2", "01UNIT3")), calls);
	}
}
