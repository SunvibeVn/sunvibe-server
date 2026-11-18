package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.application.usecase.role.OwnerAccountUseCase;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStanding;
import vn.sunvibe.auth.core.application.usecase.role.OwnerStandingUseCase;
import vn.sunvibe.property.core.application.usecase.intake.PropertyOwnershipUseCase;
import vn.sunvibe.property.core.application.usecase.intake.PropertyOwnershipView;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.UnitIntakeView;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingUseCase;
import vn.sunvibe.survey.core.application.usecase.checklist.SourceStandingView;

/**
 * The set reads of E05-S6b cost a fixed number of statements per call, whatever the size of the set (spec SR-4, S6b-D7;
 * plan E05S6b-P4, P5): counted at the JDBC level on the calling thread, for one id and for 40, on a fixture where every
 * kind of row that makes a statement conditional is present. A context of its own ({@link StatementCounter.Config}).
 * Needs Docker.
 */
@Import(StatementCounter.Config.class)
class SetReadStatementCountPostgresTest extends IntakeApiTestBase {

	/**
	 * Read 1: units 1, mappings 1, rows by ids 1, rows by leads 1, COMPLETED rows on the properties 1, the leads' system
	 * facts 2, read 2 5, suspensions 1, proofs 1, properties 1, read 4 2, OWNER holders 1, cases naming the properties 1,
	 * cases of the COMPLETED results 1, waiting and ruled intakes 1, proofs of the waiting leads 1.
	 */
	private static final long READ_1 = 22;
	/** Read 1b: properties 1, holdings 1. */
	private static final long READ_1B = 2;
	/** Read 2: checklists 1, evidence 1, media 1, media variants 1, requalifications 1. */
	private static final long READ_2 = 5;
	/** Read 4: standings 1, role names 1. */
	private static final long READ_4 = 2;

	private static final String[] CODES = {"P101", "P102", "P103", "P104", "P105"};

	@Autowired
	private StatementCounter counter;

	@Autowired
	private UnitIntakeUseCase unitIntakes;

	@Autowired
	private PropertyOwnershipUseCase ownership;

	@Autowired
	private SourceStandingUseCase sourceStandings;

	@Autowired
	private OwnerStandingUseCase ownerStandings;

	@Autowired
	private OwnerAccountUseCase owners;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private Built built;

	@Override
	String phonePrefix() {
		return "0951";
	}

	// ==================== fixture ====================

	/** A building property: a first lead created and completed, a second linked onto the same five units and completed. */
	private record Building(String propertyId, List<String> unitIds, Passed first, Issued firstIssued, Passed second,
			TestUser owner) {
	}

	private record Built(List<Building> buildings, List<String> leadIds, List<String> userIds) {

		Building one() {
			return buildings.getFirst();
		}
	}

	/** Built once, on first use: 8 properties, 16 completed leads over 40 units, and the rival of property 1. */
	private Built built() throws Exception {
		if (built == null) {
			built = build();
		}
		return built;
	}

	private Built build() throws Exception {
		List<Building> buildings = new ArrayList<>();
		for (int number = 1; number <= 8; number++) {
			buildings.add(building(unique("Counted Building " + number)));
		}
		Building one = buildings.getFirst();
		String street = jdbc.queryForObject("select street from properties where id = ?", String.class, one.propertyId());
		// property 1: a mark on its first lead's source, then a re-survey of that lead (a current PASSED and a mark)
		fixture.requalify(leader, one.first().checklistId(), "OTHER");
		fixture.revisit(leader, sale, one.first().leadId(), "PARTS", "WHOLE_BUILDING", street, "P101");
		// property 1: a rival lead whose intake waits on a decided SAME_ASSET case naming property 1
		Passed rival = fixture.parts(leader, sale, "WHOLE_BUILDING", street, "P101");
		TestUser rivalOwner = owner();
		checked(rival, rivalOwner);
		SurveyApiTestSupport.ok(createTarget(rival.leadId(), rival.spaceIds().getFirst()));
		assertEquals("CASE_OPEN", blockOk(sale, rival.leadId()).path("status").asString());
		decide(jdbc.queryForObject("select id from inventory_duplicate_cases where candidate_property_id = ? and status = 'OPEN'",
				String.class, one.propertyId()), "SAME_ASSET");
		// property 2: the owner withdrew the proof of its first lead
		Building two = buildings.get(1);
		Result withdrawal = withdraw(two.owner(), two.firstIssued().requestId(), passwordOf(two.owner()));
		assertEquals(204, withdrawal.status(), withdrawal.rawBody());
		// property 3: a whole-lead suspension; property 4: a unit suspension on P101
		suspendLead(buildings.get(2).first().leadId(), null, "Data request");
		suspendLead(buildings.get(3).first().leadId(), buildings.get(3).unitIds().getFirst(), "Room only");
		List<String> leadIds = new ArrayList<>();
		List<String> userIds = new ArrayList<>();
		for (Building building : buildings) {
			leadIds.add(building.first().leadId());
			leadIds.add(building.second().leadId());
			userIds.add(building.owner().userId());
		}
		leadIds.add(rival.leadId());
		userIds.add(rivalOwner.userId());
		return new Built(List.copyOf(buildings), List.copyOf(leadIds), List.copyOf(userIds));
	}

	private Building building(String street) throws Exception {
		Passed first = fixture.parts(leader, sale, "WHOLE_BUILDING", street, CODES);
		TestUser owner = owner();
		Issued firstIssued = checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), first.spaceIds().getFirst()));
		available(first.leadId(), first.spaceIds(), "VACANT");
		String propertyId = completed(first.leadId()).path("propertyId").asString();
		Map<String, String> unitBySpaceCode = new LinkedHashMap<>();
		jdbc.query("select space_code, unit_id from inventory_unit_mappings where intake_id = ?",
				row -> {
					unitBySpaceCode.put(row.getString("space_code"), row.getString("unit_id"));
				}, intakeRow(first.leadId()).get("id"));
		List<String> unitIds = Arrays.stream(CODES).map(unitBySpaceCode::get).toList();
		Passed second = fixture.parts(leader, sale, "WHOLE_BUILDING", street, CODES);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(), unitIds));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Building(propertyId, unitIds, first, firstIssued, second, owner);
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	/** The manager's decision, as InventoryCaseApiPostgresTest.decide makes it; the answer must be 200. */
	private void decide(String caseId, String outcome) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("outcome", outcome);
		body.put("proceedingIntakeId", null);
		body.put("basis", "Checked both files");
		body.put("expectedVersion", jdbc.queryForObject("select version from inventory_duplicate_cases where id = ?", Long.class,
				caseId));
		SurveyApiTestSupport.ok(api.post(manager, CASES + "/" + caseId + "/decision", body));
	}

	private static List<String> unknown(String kind, int count) {
		List<String> ids = new ArrayList<>();
		for (int number = 0; number < count; number++) {
			ids.add(String.format("01J%s%0" + (23 - kind.length()) + "d", kind, number));
		}
		return ids;
	}

	private static List<String> filledTo40(List<String> real, String kind) {
		List<String> ids = new ArrayList<>(real);
		ids.addAll(unknown(kind, 40 - real.size()));
		return ids;
	}

	private int cycleOf(String leadId) {
		return jdbc.queryForObject("select intent_cycle from owner_leads where id = ?", Integer.class, leadId);
	}

	private <T> T inTransaction(Supplier<T> read) {
		return new TransactionTemplate(transactionManager).execute(status -> read.get());
	}

	/** The statements the read issues, its answer kept in {@code answer}. */
	private <T> long counted(Supplier<T> read, AtomicReference<T> answer) {
		return counter.count(() -> answer.set(read.get()));
	}

	// ==================== the counts ====================

	@Test
	void forUnits_oneUnitOrForty_sameStatements() throws Exception {
		Built rows = built();
		List<String> forty = rows.buildings().stream().flatMap(building -> building.unitIds().stream()).toList();
		AtomicReference<List<UnitIntakeView>> answer = new AtomicReference<>();

		long one = counted(() -> unitIntakes.forUnits(List.of(rows.one().unitIds().getFirst())), answer);
		assertEquals(1, answer.get().size());
		long all = counted(() -> unitIntakes.forUnits(forty), answer);

		assertEquals(40, forty.size());
		assertEquals(40, answer.get().size());
		assertEquals(READ_1, one);
		assertEquals(READ_1, all);
	}

	@Test
	void ownershipOf_onePropertyOrForty_sameStatements() throws Exception {
		Built rows = built();
		List<String> forty = filledTo40(rows.buildings().stream().map(Building::propertyId).toList(), "PROP");
		AtomicReference<List<PropertyOwnershipView>> answer = new AtomicReference<>();

		long one = counted(() -> ownership.ownershipOf(List.of(rows.one().propertyId())), answer);
		assertEquals(1, answer.get().size());
		long all = counted(() -> ownership.ownershipOf(forty), answer);

		assertEquals(8, answer.get().size());
		assertEquals(READ_1B, one);
		assertEquals(READ_1B, all);
	}

	@Test
	void standings_oneLeadOrForty_sameStatements() throws Exception {
		Built rows = built();
		String resurveyed = rows.one().first().leadId();
		Map<String, Integer> forty = new LinkedHashMap<>();
		rows.leadIds().forEach(leadId -> forty.put(leadId, cycleOf(leadId)));
		unknown("LEAD", 40 - forty.size()).forEach(leadId -> forty.put(leadId, 1));
		int cycle = cycleOf(resurveyed);
		AtomicReference<Map<String, SourceStandingView>> answer = new AtomicReference<>();

		long one = counted(() -> inTransaction(() -> sourceStandings.standings(Map.of(resurveyed, cycle))), answer);
		assertEquals(1, answer.get().size());
		long all = counted(() -> inTransaction(() -> sourceStandings.standings(forty)), answer);

		assertEquals(17, rows.leadIds().size());
		assertEquals(40, answer.get().size());
		assertEquals(READ_2, one);
		assertEquals(READ_2, all);
	}

	@Test
	void ownerStandings_oneUserOrForty_sameStatements() throws Exception {
		Built rows = built();
		List<String> forty = filledTo40(rows.userIds(), "USER");
		AtomicReference<Map<String, OwnerStanding>> answer = new AtomicReference<>();

		long one = counted(() -> ownerStandings.ownerStandings(List.of(rows.one().owner().userId())), answer);
		assertEquals(1, answer.get().size());
		long all = counted(() -> ownerStandings.ownerStandings(forty), answer);

		assertEquals(40, answer.get().size());
		assertEquals(READ_4, one);
		assertEquals(READ_4, all);
	}

	// ==================== the bounds (S6b-D7, E05S6b-P5) ====================

	@Test
	void forUnitsAndOwnershipOf_251Ids_refusedWithoutAStatement() throws Exception {
		Built rows = built();
		List<String> units = new ArrayList<>(List.of(rows.one().unitIds().getFirst()));
		units.addAll(unknown("UNIT", 250));
		List<String> properties = new ArrayList<>(List.of(rows.one().propertyId()));
		properties.addAll(unknown("PROP", 250));

		long forUnits = counter.count(() -> assertThrows(IllegalArgumentException.class, () -> unitIntakes.forUnits(units)));
		long ownershipOf = counter.count(() -> assertThrows(IllegalArgumentException.class,
				() -> ownership.ownershipOf(properties)));

		assertEquals(0, forUnits);
		assertEquals(0, ownershipOf);
	}

	@Test
	void standingsAndOwnerStandings_251Ids_everyIdAnsweredInTwoChunks() throws Exception {
		Built rows = built();
		String resurveyed = rows.one().first().leadId();
		int cycle = cycleOf(resurveyed);
		Map<String, Integer> leads = new LinkedHashMap<>();
		leads.put(resurveyed, cycle);
		unknown("LEAD", 250).forEach(leadId -> leads.put(leadId, 1));
		String owner = rows.one().owner().userId();
		List<String> users = new ArrayList<>(List.of(owner));
		users.addAll(unknown("USER", 250));
		AtomicReference<Map<String, SourceStandingView>> standings = new AtomicReference<>();
		AtomicReference<Map<String, OwnerStanding>> ownerAnswer = new AtomicReference<>();

		long standingStatements = counted(() -> inTransaction(() -> sourceStandings.standings(leads)), standings);
		long ownerStatements = counted(() -> ownerStandings.ownerStandings(users), ownerAnswer);

		assertEquals(251, standings.get().size());
		assertEquals(inTransaction(() -> sourceStandings.standings(Map.of(resurveyed, cycle))).get(resurveyed),
				standings.get().get(resurveyed));
		assertEquals(READ_2 + 1, standingStatements);
		assertEquals(251, ownerAnswer.get().size());
		assertEquals(owners.ownerStanding(owner), ownerAnswer.get().get(owner));
		users.subList(1, users.size()).forEach(user -> assertEquals(new OwnerStanding(false, -1), ownerAnswer.get().get(user)));
		assertEquals(READ_4 + 1, ownerStatements);
	}
}
