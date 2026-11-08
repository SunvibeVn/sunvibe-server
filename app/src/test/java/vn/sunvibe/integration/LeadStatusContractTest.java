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

	/**
	 * The three enums agree on which statuses are closed (SV-272). Property also gets {@code IntakeLead.closed} from
	 * acquisition through its adapter; this test keeps property's own enum in step with it.
	 */
	@Test
	void closedStatuses_areTheSameInEveryModule() {
		Set<String> acquisition = Arrays.stream(OwnerLeadStatus.values()).filter(OwnerLeadStatus::isClosed)
				.map(Enum::name).collect(Collectors.toCollection(TreeSet::new));

		assertEquals(acquisition, Arrays.stream(vn.sunvibe.survey.core.domain.model.lead.LeadStatus.values())
				.filter(vn.sunvibe.survey.core.domain.model.lead.LeadStatus::isClosed)
				.map(Enum::name).collect(Collectors.toCollection(TreeSet::new)), "survey");
		assertEquals(acquisition, Arrays.stream(vn.sunvibe.property.core.domain.model.lead.LeadStatus.values())
				.filter(vn.sunvibe.property.core.domain.model.lead.LeadStatus::isClosed)
				.map(Enum::name).collect(Collectors.toCollection(TreeSet::new)), "property");
	}

	private static Set<String> names(Enum<?>[] values) {
		return Arrays.stream(values).map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
	}
}
