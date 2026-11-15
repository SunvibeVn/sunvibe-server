package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;

import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;
import vn.sunvibe.property.core.application.usecase.intake.InventoryIntakeReadinessUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeView;

/**
 * The parity of read 1 of E05-S6b with the per-lead readiness (spec SR-1, S6b-D1, S6b-D9, plan E05S6b-P3): for the leads a
 * scenario builds, one call of {@link UnitIntakeUseCase#forUnits} with every unit their readiness lists answers each unit
 * with one entry per lead whose readiness lists it, the latest COMPLETED row of that lead with the lead's state, source
 * revision and the unit's reasons, and the unit's code, property and stored availability. Both reads run in the same
 * test, on the same day.
 */
final class UnitIntakeParity {

	private final UnitIntakeUseCase unitIntakes;
	private final InventoryIntakeReadinessUseCase readiness;
	private final JdbcTemplate jdbc;

	UnitIntakeParity(UnitIntakeUseCase unitIntakes, InventoryIntakeReadinessUseCase readiness, JdbcTemplate jdbc) {
		this.unitIntakes = unitIntakes;
		this.readiness = readiness;
		this.jdbc = jdbc;
	}

	void check(String... leadIds) {
		Map<String, List<UnitIntakeView.IntakeEntry>> expected = new TreeMap<>();
		Map<String, String> codes = new HashMap<>();
		for (String leadId : leadIds) {
			IntakeBlockView.Readiness read = readiness.readiness(leadId);
			List<String> latest = jdbc.queryForList("select id from inventory_intakes where lead_id = ? and status = 'COMPLETED'"
					+ " order by intake_no desc limit 1", String.class, leadId);
			if (latest.isEmpty()) {
				assertEquals("NONE", read.state(), leadId);
				assertEquals(List.of(), read.units(), leadId);
				continue;
			}
			for (IntakeBlockView.ReadinessUnit unit : read.units()) {
				expected.computeIfAbsent(unit.unitId(), id -> new ArrayList<>()).add(new UnitIntakeView.IntakeEntry(
						latest.getFirst(), read.state(), read.sourceRevisionId(), unit.reasons()));
				codes.put(unit.unitId(), unit.unitCode());
			}
		}

		List<UnitIntakeView> answered = unitIntakes.forUnits(expected.keySet());

		assertEquals(List.copyOf(expected.keySet()), answered.stream().map(UnitIntakeView::unitId).toList());
		for (UnitIntakeView view : answered) {
			Map<String, Object> stored = jdbc.queryForMap("select property_id, availability_state from units where id = ?",
					view.unitId());
			assertEquals(stored.get("property_id"), view.propertyId(), view.unitId());
			assertEquals(stored.get("availability_state"), view.availabilityState(), view.unitId());
			assertEquals(codes.get(view.unitId()), view.unitCodeOrNull(), view.unitId());
			assertEquals(expected.get(view.unitId()).stream()
					.sorted(Comparator.comparing(UnitIntakeView.IntakeEntry::intakeId)).toList(), view.intakes(), view.unitId());
		}
		assertEquals(List.of("intakeId", "state", "sourceRevisionId", "unitReasons"),
				Arrays.stream(UnitIntakeView.IntakeEntry.class.getRecordComponents()).map(RecordComponent::getName).toList());
	}
}
