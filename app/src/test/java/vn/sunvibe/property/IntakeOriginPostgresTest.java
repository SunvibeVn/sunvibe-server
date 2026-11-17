package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginUseCase;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginView;
import vn.sunvibe.property.core.application.usecase.intake.IntakeOriginView.UnitSpace;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The origin of a completed intake, published for the survey proposal of an agreement draft (brokerage-agreement E05-S3
 * spec S3-D17, ED-16): the lead, the intent cycle, the source revision and the space each mapped unit came from.
 * Needs Docker.
 */
class IntakeOriginPostgresTest extends IntakeApiTestBase {

	private static final Comparator<UnitSpace> BY_SPACE = Comparator.comparing(UnitSpace::spaceId);

	@Autowired
	private IntakeOriginUseCase origins;

	@Override
	String phonePrefix() {
		return "0945";
	}

	@Test
	void originOf_completedIntake_leadCycleRevisionAndPairs() throws Exception {
		Passed passed = fixture.parts(leader, sale, "WHOLE_BUILDING", unique("Origin Building"), "P101", "P102");
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), passed.spaceIds().get(0)));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		completed(passed.leadId());
		String intakeId = (String) intakeRow(passed.leadId()).get("id");

		IntakeOriginView origin = origins.originOf(intakeId).orElseThrow();

		assertEquals(passed.leadId(), origin.leadId());
		assertEquals(jdbc.queryForObject("select intent_cycle from owner_leads where id = ?", Integer.class,
				passed.leadId()), origin.intentCycle());
		assertEquals(passed.checklistId(), origin.sourceRevisionId());
		List<UnitSpace> stored = jdbc.query("select unit_id, space_id from inventory_unit_mappings where intake_id = ?",
				(row, index) -> new UnitSpace(row.getString("unit_id"), row.getString("space_id")), intakeId);
		assertEquals(2, stored.size());
		assertEquals(stored.stream().sorted(BY_SPACE).toList(), origin.units().stream().sorted(BY_SPACE).toList());
		assertEquals(passed.spaceIds().stream().sorted().toList(),
				origin.units().stream().map(UnitSpace::spaceId).sorted().toList());
	}

	@Test
	void originOf_unknownOrNotCompleted_empty() throws Exception {
		assertTrue(origins.originOf("01JUNKNOWN0000000000000000").isEmpty());

		Passed passed = house(unique("Origin House"));
		proved(passed, owner());
		Map<String, Object> intake = intakeRow(passed.leadId());
		assertNotEquals("COMPLETED", intake.get("status"));

		assertTrue(origins.originOf((String) intake.get("id")).isEmpty());
	}
}
