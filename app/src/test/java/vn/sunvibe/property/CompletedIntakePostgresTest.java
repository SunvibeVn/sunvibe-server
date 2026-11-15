package vn.sunvibe.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeUseCase;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeView;
import vn.sunvibe.property.core.application.usecase.intake.CompletedIntakeView.OwnershipHolding;
import vn.sunvibe.property.core.application.usecase.intake.InventoryIntakeReadinessUseCase;
import vn.sunvibe.property.core.domain.policy.intake.AddressNormalizer;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository;
import vn.sunvibe.property.core.domain.repository.InventoryReadRepository.HoldingFacts;
import vn.sunvibe.survey.PassedRevisionFixture.Passed;
import vn.sunvibe.survey.SurveyApiTestSupport;

/**
 * The latest completed intake of a lead with its readiness and the ownership of its result property today, published
 * for brokerage-agreement eligibility (E05-S1 spec 5.4, S1-D6, EL-12). Needs Docker.
 */
class CompletedIntakePostgresTest extends IntakeApiTestBase {

	@Autowired
	private CompletedIntakeUseCase completedIntakes;

	@Autowired
	private InventoryIntakeReadinessUseCase readiness;

	@Autowired
	private InventoryReadRepository inventory;

	@Override
	String phonePrefix() {
		return "0952";
	}

	// ==================== fixtures ====================

	private record Done(Passed passed, TestUser owner, JsonNode result) {
		String leadId() {
			return passed.leadId();
		}

		String propertyId() {
			return result.path("propertyId").asString();
		}
	}

	private Done done() throws Exception {
		Passed passed = house(unique("Bạch Đằng"));
		TestUser owner = owner();
		checked(passed, owner);
		SurveyApiTestSupport.ok(createTarget(passed.leadId(), null));
		available(passed.leadId(), passed.spaceIds(), "VACANT");
		return new Done(passed, owner, completed(passed.leadId()));
	}

	private record Pair(Passed first, String propertyId, Map<String, String> unitBySpaceCode, Passed second) {
	}

	/** Two houses on one street: the first creates the property, the second links onto its one unit; both completed. */
	private Pair housePair() throws Exception {
		String street = unique("Two Leads");
		Passed first = house(street);
		TestUser owner = owner();
		checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), null));
		available(first.leadId(), first.spaceIds(), "VACANT");
		JsonNode result = completed(first.leadId());
		String propertyId = result.path("propertyId").asString();
		String unit = result.path("units").path(0).path("unitId").asString();
		Passed second = house(street);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(), List.of(unit)));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Pair(first, propertyId, Map.of("A1", unit), second);
	}

	/**
	 * A building lead with P101 and P102 created and completed, and a second building lead on the same street linked
	 * space by space onto the units of the same codes.
	 */
	private Pair buildingPair(String... secondCodes) throws Exception {
		String street = unique("Two Leads Building");
		Passed first = fixture.parts(leader, sale, "WHOLE_BUILDING", street, "P101", "P102");
		TestUser owner = owner();
		checked(first, owner);
		SurveyApiTestSupport.ok(createTarget(first.leadId(), first.spaceIds().get(0)));
		available(first.leadId(), first.spaceIds(), "VACANT");
		String propertyId = completed(first.leadId()).path("propertyId").asString();
		Map<String, String> unitBySpaceCode = new LinkedHashMap<>();
		jdbc.query("select space_code, unit_id from inventory_unit_mappings where intake_id = ?",
				row -> {
					unitBySpaceCode.put(row.getString("space_code"), row.getString("unit_id"));
				}, intakeRow(first.leadId()).get("id"));
		Passed second = fixture.parts(leader, sale, "WHOLE_BUILDING", street, secondCodes);
		checked(second, owner);
		SurveyApiTestSupport.ok(linkTarget(second.leadId(), propertyId, second.spaceIds(),
				Arrays.stream(secondCodes).map(unitBySpaceCode::get).toList()));
		available(second.leadId(), second.spaceIds(), "VACANT");
		completed(second.leadId());
		return new Pair(first, propertyId, unitBySpaceCode, second);
	}

	private void suspendLead(String leadId, String unitIdOrNull, String reason) throws Exception {
		Result answer = suspend(manager, leadId, unitIdOrNull, reason, versionOf(manager, leadId));
		assertEquals(200, answer.status(), answer.rawBody());
	}

	/** An ownership row as {@link #ownership} inserts it, with the relation and the validity named. */
	private void ownershipRow(String propertyId, String userId, String relation, LocalDate from, LocalDate toOrNull) {
		jdbc.update("insert into property_ownerships (id, property_id, user_id, relation_type, verification_state, valid_from,"
						+ " valid_to, created_at) values (?, ?, ?, ?, 'UNVERIFIED', ?, ?, ?)", ids.generateId(), propertyId, userId,
				relation, from, toOrNull, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
	}

	private void assertParity(String leadId) {
		assertEquals(readiness.readiness(leadId), completedIntakes.latest(leadId).orElseThrow().readiness());
	}

	private static final Comparator<OwnershipHolding> HOLDING_ORDER = Comparator.comparing(OwnershipHolding::userId)
			.thenComparing(OwnershipHolding::relationType);

	// ==================== the basis ====================

	@Test
	void latest_unknownLeadOrNoCompletedIntake_empty() throws Exception {
		assertTrue(completedIntakes.latest("01JUNKNOWN0000000000000000").isEmpty());
		assertTrue(completedIntakes.latest("not-a-ulid").isEmpty());
		assertTrue(completedIntakes.latest(house(unique("Pasteur")).leadId()).isEmpty());
	}

	@Test
	void latest_basisIds_ofTheLatestCompletedRow() throws Exception {
		Done done = done();

		CompletedIntakeView view = completedIntakes.latest(done.leadId()).orElseThrow();

		assertEquals(intakeRow(done.leadId()).get("id"), view.intakeId());
		assertEquals(done.propertyId(), view.propertyId());
		String ownerId = jdbc.queryForObject("select owner_id from properties where id = ?", String.class, done.propertyId());
		assertEquals(ownerId, view.ownerIdOrNull());
		assertEquals(done.owner().userId(), view.ownerIdOrNull());
	}

	// ==================== EL-12: the same readiness as the published one ====================

	@Test
	void latest_readiness_equalsThePublishedReadiness() throws Exception {
		Done usable = done();
		assertParity(usable.leadId());

		Pair houses = housePair();
		suspendLead(houses.first().leadId(), null, "Data request");
		assertParity(houses.second().leadId());

		Pair building = buildingPair("P101", "P102");
		suspendLead(building.first().leadId(), building.unitBySpaceCode().get("P101"), "Room only");
		assertParity(building.second().leadId());

		Done coOwned = done();
		ownershipRow(coOwned.propertyId(), ids.generateId(), "CO_OWNER", LocalDate.of(2026, 1, 1), null);
		assertParity(coOwned.leadId());
	}

	// ==================== the ownership valid today ====================

	@Test
	void latest_holdings_everyRowValidTodayOnly() throws Exception {
		Done done = done();
		String b = ids.generateId();
		String c = ids.generateId();
		String d = ids.generateId();
		ownershipRow(done.propertyId(), b, "OWNER", LocalDate.of(2026, 1, 1), today().minusDays(1));
		ownershipRow(done.propertyId(), c, "OWNER", today().plusDays(1), null);
		ownershipRow(done.propertyId(), d, "AUTHORIZED_REPRESENTATIVE", LocalDate.of(2026, 1, 1), null);
		jdbc.update("update property_ownerships set verification_state = 'REJECTED' where property_id = ? and user_id = ?",
				done.propertyId(), done.owner().userId());

		List<OwnershipHolding> holdings = completedIntakes.latest(done.leadId()).orElseThrow().holdings();

		List<OwnershipHolding> expected = Stream.of(
						new OwnershipHolding(done.owner().userId(), "OWNER", "REJECTED"),
						new OwnershipHolding(d, "AUTHORIZED_REPRESENTATIVE", "UNVERIFIED"))
				.sorted(HOLDING_ORDER).toList();
		assertEquals(expected, holdings);
	}

	@Test
	void holdingsOn_rowsValidOnTheDay_anyRelation() {
		LocalDate day = LocalDate.of(2026, 11, 12);
		String street = unique("Holding Rows");
		String a = ids.generateId();
		String id = property(a, WARD, "Hải Châu", street, "HOUSE", AddressNormalizer.normalize(street));
		ownership(id, a);
		ownershipRow(id, ids.generateId(), "OWNER", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
		ownershipRow(id, ids.generateId(), "OWNER", LocalDate.of(2026, 12, 12), null);
		String d = ids.generateId();
		ownershipRow(id, d, "CO_OWNER", LocalDate.of(2026, 1, 1), null);

		List<HoldingFacts> expected = Stream.of(new HoldingFacts(a, "OWNER", "UNVERIFIED"),
						new HoldingFacts(d, "CO_OWNER", "UNVERIFIED"))
				.sorted(Comparator.comparing(HoldingFacts::userId).thenComparing(HoldingFacts::relationType)).toList();
		assertEquals(expected, inventory.holdingsOn(id, day));
	}
}
