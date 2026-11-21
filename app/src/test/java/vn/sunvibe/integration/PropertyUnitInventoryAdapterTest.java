package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import vn.sunvibe.listing.core.application.shared.external.property.UnitAddressFacts;
import vn.sunvibe.listing.core.application.shared.external.property.UnitAvailabilityFacts;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeView;
import vn.sunvibe.property.core.application.usecase.intake.UnitLocationView;

/**
 * The adapter only translates property-service's published unit reads into listing's port (listing-publish E06-S4 spec
 * E06S4-D13, ADR-009; RV-15): the availability as stored, whether any intake entry of the unit lists
 * {@code AVAILABILITY_DIFFERS}, the 250 bound before any call, and the real location of one unit, none named by its text.
 */
class PropertyUnitInventoryAdapterTest {

	private final List<List<String>> intakeCalls = new ArrayList<>();
	private final List<List<String>> locationCalls = new ArrayList<>();
	private List<UnitIntakeView> intakeViews = List.of();
	private List<UnitLocationView> locationViews = List.of();

	private final PropertyUnitInventoryAdapter adapter = new PropertyUnitInventoryAdapter(
			unitIds -> {
				intakeCalls.add(List.copyOf(unitIds));
				return intakeViews;
			},
			unitIds -> {
				locationCalls.add(List.copyOf(unitIds));
				return locationViews;
			});

	@Test
	void availability_fieldsCopied_differsWhenAnyEntryLists() {
		intakeViews = List.of(new UnitIntakeView("01UNIT1", "P101", "01PROP1", "OCCUPIED", List.of(
				new UnitIntakeView.IntakeEntry("01INT1", "USABLE", "01REV1", List.of("RESTRICTED")),
				new UnitIntakeView.IntakeEntry("01INT2", "USABLE", "01REV2", List.of("AVAILABILITY_DIFFERS", "UNIT_STOP")))));

		List<UnitAvailabilityFacts> facts = adapter.availability(List.of("01UNIT1", "01UNIT9"));

		assertEquals(List.of(new UnitAvailabilityFacts("01UNIT1", "P101", "OCCUPIED", true)), facts);
		assertEquals(List.of(List.of("01UNIT1", "01UNIT9")), intakeCalls);
		assertEquals(List.of(), locationCalls);
	}

	@Test
	void availability_noEntryLists_differsFalse() {
		intakeViews = List.of(
				new UnitIntakeView("01UNIT1", null, "01PROP1", "AVAILABLE", List.of(
						new UnitIntakeView.IntakeEntry("01INT1", "SUSPENDED", "01REV1", List.of("RESTRICTED", "UNIT_STOP")))),
				new UnitIntakeView("01UNIT2", "P102", "01PROP1", "UNKNOWN_STATE", List.of()));

		List<UnitAvailabilityFacts> facts = adapter.availability(List.of("01UNIT1", "01UNIT2"));

		assertEquals(List.of(new UnitAvailabilityFacts("01UNIT1", null, "AVAILABLE", false),
				new UnitAvailabilityFacts("01UNIT2", "P102", "UNKNOWN_STATE", false)), facts);
	}

	@Test
	void availability_moreThan250Ids_refusedBeforeCall() {
		List<String> ids = IntStream.rangeClosed(1, 251).mapToObj(index -> "01UNIT" + index).toList();

		assertThrows(IllegalArgumentException.class, () -> adapter.availability(ids));

		assertEquals(List.of(), intakeCalls);
		adapter.availability(ids.subList(0, 250));
		assertEquals(1, intakeCalls.size());
		assertEquals(250, intakeCalls.getFirst().size());
	}

	@Test
	void address_fieldsCopied_toStringNamesNoValue() {
		locationViews = List.of(new UnitLocationView("01UNIT1", "P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau"));

		Optional<UnitAddressFacts> address = adapter.address("01UNIT1");

		assertEquals(Optional.of(new UnitAddressFacts("01UNIT1", "P101", "P101 · Floor 1", "12 Tran Phu", "Hai Chau")),
				address);
		assertEquals(List.of(List.of("01UNIT1")), locationCalls);
		String text = address.orElseThrow().toString();
		assertTrue(text.contains("01UNIT1"), text);
		for (String value : List.of("P101", "Floor", "Tran Phu", "Hai Chau")) {
			assertFalse(text.contains(value), value);
		}
	}

	@Test
	void address_unitUnknown_empty() {
		locationViews = List.of();

		assertEquals(Optional.empty(), adapter.address("01UNIT1"));
		assertEquals(List.of(List.of("01UNIT1")), locationCalls);
	}
}
