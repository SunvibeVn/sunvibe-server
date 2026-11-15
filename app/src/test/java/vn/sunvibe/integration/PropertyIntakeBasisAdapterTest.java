package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.brokerage.core.application.shared.external.property.IntakeBasisFacts;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeView;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeView.OwnershipHolding;
import vn.sunvibe.property.core.application.usecase.intake.IntakeBlockView;

/** The adapter only copies property-service's completed intake into brokerage's terms (E05-S1 spec 5.5, ADR-009). */
class PropertyIntakeBasisAdapterTest {

	private static final IntakeBlockView.Readiness READINESS = new IntakeBlockView.Readiness("USABLE", List.of(),
			"01REVISION", List.of(new IntakeBlockView.ReadinessUnit("01UNIT1", "P101", true, List.of()),
					new IntakeBlockView.ReadinessUnit("01UNIT2", null, false, List.of("PROPERTY_STOP", "UNIT_STOP"))),
			List.of("AGREEMENT"));
	private static final CompletedIntakeView VIEW = new CompletedIntakeView("01INTAKE", "01PROPERTY", READINESS, "O",
			List.of(new OwnershipHolding("O", "OWNER", "VERIFIED"), new OwnershipHolding("X", "CO_OWNER", "UNVERIFIED")));

	private final List<String> calls = new ArrayList<>();

	@Test
	void latestBasis_mapsEveryField() {
		PropertyIntakeBasisAdapter adapter = new PropertyIntakeBasisAdapter(leadId -> {
			calls.add(leadId);
			return Optional.of(VIEW);
		});

		IntakeBasisFacts basis = adapter.latestBasis("01LEAD").orElseThrow();

		assertEquals(new IntakeBasisFacts("01INTAKE", "01PROPERTY", "USABLE",
				List.of(new IntakeBasisFacts.Unit("01UNIT1", "P101", List.of()),
						new IntakeBasisFacts.Unit("01UNIT2", null, List.of("PROPERTY_STOP", "UNIT_STOP"))),
				"O", List.of(new IntakeBasisFacts.Holding("O", "OWNER", "VERIFIED"),
						new IntakeBasisFacts.Holding("X", "CO_OWNER", "UNVERIFIED"))), basis);
		assertEquals(List.of("01LEAD"), calls);
	}

	@Test
	void latestBasis_none_empty() {
		PropertyIntakeBasisAdapter adapter = new PropertyIntakeBasisAdapter(leadId -> Optional.empty());

		assertTrue(adapter.latestBasis("01LEAD").isEmpty());
	}
}
