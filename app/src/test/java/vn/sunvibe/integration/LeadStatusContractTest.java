package vn.sunvibe.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.survey.core.application.shared.external.lead.LeadMoveTarget;

/**
 * The lead status names survey and property read are the names acquisition writes (clean-up spec section 7). Each
 * module keeps its own enum, as ADR-009 asks, and this test keeps the three equal, so a name the acquisition adapters
 * pass on always has a constant in the reading module.
 */
class LeadStatusContractTest {

	@Test
	void statusNames_areTheSameInEveryModule() {
		Set<String> acquisition = names(OwnerLeadStatus.values());

		assertEquals(acquisition, names(vn.sunvibe.survey.core.domain.model.lead.LeadStatus.values()), "survey");
		assertEquals(acquisition, names(vn.sunvibe.property.core.domain.model.lead.LeadStatus.values()), "property");
	}

	/** Every status a survey command can ask for is a lead status; KEEP asks for none. */
	@Test
	void moveTargets_areLeadStatuses() {
		for (LeadMoveTarget target : LeadMoveTarget.values()) {
			if (target != LeadMoveTarget.KEEP) {
				assertDoesNotThrow(() -> vn.sunvibe.survey.core.domain.model.lead.LeadStatus.valueOf(target.name()),
						target.name());
			}
		}
	}

	private static Set<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
	}
}
