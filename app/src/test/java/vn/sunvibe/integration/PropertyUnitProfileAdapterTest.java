package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.sunvibe.listing.core.application.shared.external.property.UnitProfileFacts;
import vn.sunvibe.property.core.application.usecase.intake.UnitProfileUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitProfileView;

/**
 * The adapter answers listing's unit profile port with property's published, viewer-less profile read
 * (listing-publish E06-S2 spec E06S2-D12; ADR-009): one call of one id, every field copied, absence kept as absence.
 */
class PropertyUnitProfileAdapterTest {

	private final FakeProfiles profiles = new FakeProfiles();
	private final PropertyUnitProfileAdapter adapter = new PropertyUnitProfileAdapter(profiles);

	@Test
	void profile_knownUnit_everyFieldCopied() {
		profiles.answer = List.of(new UnitProfileView("01UNIT1", "APARTMENT", "48", "Đà Nẵng", "20242", "Phường Hải Châu",
				3, 2, 45.5));

		assertEquals(Optional.of(new UnitProfileFacts("01UNIT1", "APARTMENT", "48", "Đà Nẵng", "20242",
				"Phường Hải Châu", 3, 2, 45.5)), adapter.profile("01UNIT1"));
		profiles.answer = List.of(new UnitProfileView("01UNIT2", null, null, null, null, null, 0, 0, 0.0));
		assertEquals(Optional.of(new UnitProfileFacts("01UNIT2", null, null, null, null, null, 0, 0, 0.0)),
				adapter.profile("01UNIT2"));
	}

	@Test
	void profile_unknownUnit_empty() {
		profiles.answer = List.of();

		assertEquals(Optional.empty(), adapter.profile("01UNKNOWN"));
	}

	@Test
	void profile_oneUnit_oneCallOfOneId() {
		profiles.answer = List.of(new UnitProfileView("01UNIT1", "HOUSE", null, null, null, null, 1, 1, 30.0));

		adapter.profile("01UNIT1");

		assertEquals(List.of(List.of("01UNIT1")), profiles.calls);
	}

	/** Records each call and answers what the test set. */
	private static final class FakeProfiles implements UnitProfileUseCase {

		private final List<List<String>> calls = new ArrayList<>();
		private List<UnitProfileView> answer = List.of();

		@Override
		public List<UnitProfileView> profiles(Collection<String> unitIds) {
			calls.add(List.copyOf(unitIds));
			return answer;
		}
	}
}
