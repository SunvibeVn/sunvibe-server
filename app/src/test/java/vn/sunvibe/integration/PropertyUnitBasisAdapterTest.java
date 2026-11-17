package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.PropertyOwnerFacts;
import vn.sunvibe.brokerage.core.application.shared.external.property.UnitBasisFacts;
import vn.sunvibe.property.core.application.usecase.intake.PropertyOwnershipView;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeView;

/**
 * The adapter answers brokerage's set reads of the units' bases and the properties' owners with property-service's
 * reads 1 and 1b, and only translates (E05-S6 spec S6-D17; ADR-009): the availability and the source revision, which
 * E05 does not compare, are dropped.
 */
class PropertyUnitBasisAdapterTest {

	private final List<String> calls = new ArrayList<>();

	private final PropertyUnitBasisAdapter adapter = new PropertyUnitBasisAdapter(unitIds -> {
		calls.add("forUnits " + unitIds);
		return List.of(new UnitIntakeView("U1", "P101", "PR1", "AVAILABLE", List.of(
				new UnitIntakeView.IntakeEntry("I1", "USABLE", "REV1", List.of("AVAILABILITY_DIFFERS", "UNIT_STOP")),
				new UnitIntakeView.IntakeEntry("I2", "SUSPENDED", "REV2", List.of()))),
				new UnitIntakeView("U2", null, "PR1", "RENTED", List.of()));
	}, propertyIds -> {
		calls.add("ownershipOf " + propertyIds);
		return List.of(new PropertyOwnershipView("PR1", "O1", List.of(
				new PropertyOwnershipView.Holding("O1", "OWNER", "UNVERIFIED"),
				new PropertyOwnershipView.Holding("C1", "CO_OWNER", "VERIFIED"))),
				new PropertyOwnershipView("PR2", null, List.of()));
	});

	@Test
	void forUnits_entriesTranslated_availabilityAndRevisionDropped() {
		List<UnitBasisFacts> facts = adapter.forUnits(List.of("U1", "U2"));

		assertEquals(List.of(new UnitBasisFacts("U1", "PR1", List.of(
						new UnitBasisFacts.Entry("I1", "USABLE", List.of("AVAILABILITY_DIFFERS", "UNIT_STOP")),
						new UnitBasisFacts.Entry("I2", "SUSPENDED", List.of()))),
				new UnitBasisFacts("U2", "PR1", List.of())), facts);
		assertEquals(List.of("forUnits [U1, U2]"), calls);
	}

	@Test
	void ownersOf_declaredOwnerAndHoldingsTranslated() {
		List<PropertyOwnerFacts> facts = adapter.ownersOf(List.of("PR1", "PR2"));

		assertEquals(List.of(new PropertyOwnerFacts("PR1", "O1", List.of(
						new IntakeBasisFacts.Holding("O1", "OWNER", "UNVERIFIED"),
						new IntakeBasisFacts.Holding("C1", "CO_OWNER", "VERIFIED"))),
				new PropertyOwnerFacts("PR2", null, List.of())), facts);
		assertEquals(List.of("ownershipOf [PR1, PR2]"), calls);
	}
}
