package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import vn.sunvibe.property.core.application.usecase.intake.UnitLocationUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationView;

/**
 * The viewer-less unit location read of brokerage-agreement E05-S5 (spec S5-D2, PR-1): per unit its code, its location
 * label, the property's street and ward; an unknown id left out; two statements whatever the size of the set, at most
 * 250 ids. The annotations of {@link SetReadStatementCountPostgresTest}, so both share one context. Needs Docker.
 */
@Import(StatementCounter.Config.class)
class UnitLocationReadPostgresTest extends IntakeApiTestBase {

	@Autowired
	private StatementCounter counter;

	@Autowired
	private UnitLocationUseCase locations;

	@Override
	String phonePrefix() {
		return "0962";
	}

	@Test
	void locate_intakeLegacyAndUnknown_valuesOrLeftOut() {
		String intakeProperty = property(sale.userId(), WARD, "Phường Hải Châu", unique("Location Street"), "HOUSE", null);
		String intakeUnit = unit(intakeProperty, "P101", "AVAILABLE");
		jdbc.update("update units set location_label = ? where id = ?", "P101 · Floor 1, front room", intakeUnit);
		String legacyProperty = property(sale.userId(), null, "Hải Châu", unique("Legacy Street"), "HOUSE", null);
		String legacyUnit = unit(legacyProperty, "L1", "AVAILABLE");
		String unknown = "01JUNKNOWNUNIT000000000000";
		String street = jdbc.queryForObject("select street from properties where id = ?", String.class, intakeProperty);
		String legacyStreet = jdbc.queryForObject("select street from properties where id = ?", String.class,
				legacyProperty);

		Map<String, UnitLocationView> found = byUnit(locations.locate(List.of(intakeUnit, legacyUnit, unknown)));

		assertEquals(2, found.size(), found.toString());
		assertEquals(new UnitLocationView(intakeUnit, "P101", "P101 · Floor 1, front room", street, "Phường Hải Châu"),
				found.get(intakeUnit));
		assertEquals(new UnitLocationView(legacyUnit, "L1", null, legacyStreet, "Hải Châu"), found.get(legacyUnit));
		assertEquals(List.of(), locations.locate(List.of()));
	}

	@Test
	void locate_250Units_twoStatements() {
		String propertyId = property(sale.userId(), WARD, "Phường Hải Châu", unique("Counted Location Street"), "HOUSE",
				null);
		List<String> units = new ArrayList<>();
		for (int number = 0; number < 5; number++) {
			units.add(unit(propertyId, "C" + number, "AVAILABLE"));
		}
		List<String> all = new ArrayList<>(units);
		while (all.size() < 250) {
			all.add(String.format("01JUNKNOWNUNIT%012d", all.size()));
		}
		AtomicReference<List<UnitLocationView>> answer = new AtomicReference<>();

		long statements = counter.count(() -> answer.set(locations.locate(all)));

		assertEquals(250, all.size());
		assertEquals(5, answer.get().size());
		assertEquals(2, statements);
	}

	@Test
	void locate_251Units_refused() {
		List<String> ids = new ArrayList<>();
		for (int number = 0; number < 251; number++) {
			ids.add(String.format("01JUNKNOWNUNIT%012d", number));
		}

		long statements = counter.count(() -> assertThrows(IllegalArgumentException.class, () -> locations.locate(ids)));

		assertEquals(0, statements);
	}

	private static Map<String, UnitLocationView> byUnit(List<UnitLocationView> views) {
		return views.stream().collect(Collectors.toMap(UnitLocationView::unitId, Function.identity()));
	}
}
