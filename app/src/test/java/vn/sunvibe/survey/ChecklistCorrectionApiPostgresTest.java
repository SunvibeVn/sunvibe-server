package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.domain.policy.SurveyTimeRules;

/**
 * The correction rule end to end (survey-checklist BR-22, BR-27, spec 4.5, A-19; W16-16 and W16-44): Sale A performs
 * the visit and saves, the lead goes to Sale B, B takes the draft over and changes A's records. Each case says whether
 * B needs a correction; the domain test of every case is {@code CorrectionPolicyTest}. Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false" })
@ActiveProfiles("test")
@Import(PostgresContainerConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChecklistCorrectionApiPostgresTest {

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private UserDomainRepository userRepository;

	@Autowired
	private BCryptPasswordEncoder passwordEncoder;

	@Autowired
	private IdGenerator<String> ulidGenerator;

	@Autowired
	private JdbcTemplate jdbc;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private TestUser saleA;
	private TestUser saleB;
	private TestUser leader;
	private LocalDate today;

	/** A draft handed from A to B: the visit, the checklist and the G1 space ids in order. */
	private record Draft(Visit visit, String id, List<String> spaces) {

		String space() {
			return spaces.get(0);
		}
	}

	/** What A saves before the handover. */
	@FunctionalInterface
	private interface Prepare {
		void run(Draft draft) throws Exception;
	}

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0965");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		saleA = survey.user("Correction Sale A", "SALE");
		saleB = survey.user("Correction Sale B", "SALE");
		leader = survey.user("Correction Leader", "SALE_LEADER");
		today = LocalDate.now(SurveyTimeRules.ZONE);
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W16-16 ====================

	@Test
	void fillingAFieldANeverSet_needsNoCorrection_andKeepsThePerformerAsObserver() throws Exception {
		Draft draft = handedOver("WHOLE", 1, d -> { });

		JsonNode saved = checklists.saved(saleB, draft.id(), "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("30"), "MEASURED")));

		JsonNode meta = area(saved).path("meta");
		assertEquals(saleB.userId(), meta.path("enteredByUserId").asString());
		assertEquals(saleA.userId(), meta.path("observedByUserId").asString());
		assertEquals(draft.visit().arrivalDate().toString(), meta.path("observedOn").asString());
	}

	@Test
	void changingWhatAObserved_needsACorrection_inEveryGroup() throws Exception {
		String hazardId = checklists.newId();
		Draft draft = handedOver("WHOLE", 1, d -> {
			checklists.saved(saleA, d.id(), "parts", c -> set(c, "spaces.0.floor", checklists.value("Ground")));
			checklists.saved(saleA, d.id(), "dimensions", c -> set(c, "spaces.0.area",
					checklists.sourced(new BigDecimal("30"), "MEASURED")));
			checklists.saved(saleA, d.id(), "condition", c -> set(c, "spaces.0.kitchen",
					checklists.value(checklists.areaCheck("INSPECTED", true, null, null))));
			checklists.saved(saleA, d.id(), "safety", c -> set(c, "hazards", checklists.value(List.of(
					checklists.hazard(hazardId, "MINOR", "OPEN", null)))));
			checklists.saved(saleA, d.id(), "amenities", c -> ((ArrayNode) c.path("sharedItems")).add(
					item("AIR_CONDITIONER", "YES", null, null)));
			checklists.saved(saleA, d.id(), "terms", c -> {
				set(c, "spaces.0.monthlyRent", checklists.sourced(checklists.rent(10_000_000), "OBSERVED_ON_SITE"));
				set(c, "common.electricity", checklists.sourced(checklists.charge("AMOUNT", 3_500L, "PER_KWH"),
						"OBSERVED_ON_SITE"));
			});
		});

		needsCorrection(draft, "identification", c -> set(c, "propertyType", checklists.value("VILLA")));
		needsCorrection(draft, "identification", c -> c.put("rentalScope", "PARTS"));
		needsCorrection(draft, "parts", c -> set(c, "spaces.0.floor", checklists.value("First")));
		JsonNode corrected = needsCorrection(draft, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("31"), "MEASURED")));
		needsCorrection(draft, "condition", c -> set(c, "spaces.0.kitchen", checklists.value(checklists.areaCheck(
				"NOT_ENTERED", null, null, "Locked"))));
		needsCorrection(draft, "safety", c -> ((ObjectNode) c.path("hazards").path("value").path(0))
				.put("severity", "IMPORTANT"));
		needsCorrection(draft, "amenities", c -> ((ObjectNode) c.path("sharedItems").path(0))
				.put("presence", "NO"));
		needsCorrection(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.sourced(
				checklists.rent(11_000_000), "OBSERVED_ON_SITE")));
		needsCorrection(draft, "terms", c -> set(c, "common.electricity", checklists.sourced(
				checklists.charge("AMOUNT", 4_000L, "PER_KWH"), "OBSERVED_ON_SITE")));

		Map<String, Object> row = jdbc.queryForMap("select id, correction_reason, correction_basis_confirmed_on, "
				+ "corrected_units from survey_checklist_saves where checklist_id = ? and group_key = 'DIMENSIONS' "
				+ "order by saved_at desc, id desc limit 1", draft.id());
		assertEquals("Measured again", row.get("correction_reason"));
		assertTrue(((String) row.get("corrected_units")).contains("dimensions/spaces/" + draft.space() + "/area"));
		JsonNode meta = area(corrected).path("meta");
		assertEquals(String.valueOf(row.get("id")), meta.path("correctionSaveId").asString());
		assertEquals(saleA.userId(), meta.path("observedByUserId").asString());
		JsonNode correction = correctionOf(corrected, String.valueOf(row.get("id")));
		assertEquals("DIMENSIONS", correction.path("group").asString());
		assertEquals("dimensions/spaces/" + draft.space() + "/area", correction.path("units").path(0)
				.path("unitKey").asString());
		assertEquals("P101", correction.path("units").path(0).path("spaceCode").asString());

		needsCorrection(draft, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("32"), "MEASURED")));

		Result checkpoint = checklists.checkpoint(saleB, draft.id(), 1);
		assertEquals(200, checkpoint.status(), checkpoint.rawBody());
		assertEquals(0, new BigDecimal("30").compareTo(checkpoint.body().path("snapshot").path("groups")
				.path("dimensions").path("content").path("spaces").path(0).path("area").path("value")
				.decimalValue()));
	}

	@Test
	void removingAsSpace_needsACorrection_coveringTheCascadedUnits_withTheSpaceCode() throws Exception {
		Draft draft = handedOver("PARTS", 2, d -> checklists.saved(saleA, d.id(), "dimensions",
				c -> set(c, "spaces.1.area", checklists.sourced(new BigDecimal("25"), "MEASURED"))));
		String removed = draft.spaces().get(1);

		JsonNode saved = needsCorrection(draft, "identification", c -> ((ArrayNode) c.path("spaces")).remove(1));

		Map<String, Object> g1Row = jdbc.queryForMap("select id, corrected_units from survey_checklist_saves where "
				+ "checklist_id = ? and group_key = 'IDENTIFICATION' order by saved_at desc, id desc limit 1",
				draft.id());
		String units = (String) g1Row.get("corrected_units");
		assertTrue(units.contains("identification/spaces/" + removed), units);
		assertTrue(units.contains("dimensions/spaces/" + removed + "/area"), units);
		assertEquals(null, jdbc.queryForObject("select correction_reason from survey_checklist_saves where "
				+ "checklist_id = ? and group_key = 'DIMENSIONS' order by saved_at desc, id desc limit 1",
				String.class, draft.id()));
		JsonNode correction = correctionOf(saved, String.valueOf(g1Row.get("id")));
		List<String> codes = new ArrayList<>();
		correction.path("units").forEach(unit -> codes.add(unit.path("spaceCode").asString()));
		assertTrue(codes.contains("P102"), correction.toString());
	}

	@Test
	void ownerStatementsOfA_needACorrectionToEditOrRemove_butNotToReplaceWithANewerOne() throws Exception {
		LocalDate stated = today.minusDays(2);
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "terms", c -> {
			set(c, "spaces.0.monthlyRent", checklists.ownerStated(checklists.rent(10_000_000), stated,
					saleA.userId(), null));
			set(c, "common.electricity", checklists.sourced(checklists.charge("AMOUNT", 3_500L, "PER_KWH"),
					"OBSERVED_ON_SITE"));
		}));

		assertNeedsCorrection(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.ownerStated(
				checklists.rent(9_000_000), stated, saleA.userId(), "A told me")));
		assertNeedsCorrection(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.unknown(true)));
		assertNeedsCorrection(draft, "terms", c -> set(c, "common.electricity", checklists.ownerStated(
				checklists.charge("AMOUNT", 3_000L, "PER_KWH"), today, saleB.userId(), null)));
		noCorrection(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.ownerStated(
				checklists.rent(9_500_000), today, saleB.userId(), null)));
	}

	@Test
	void replacingAnOwnerFormRent_needsNoCorrection() throws Exception {
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "terms", c -> set(c,
				"spaces.0.monthlyRent", checklists.sourced(checklists.rent(12_000_000), "OWNER_FORM"))));

		noCorrection(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.sourced(
				checklists.rent(11_000_000), "OBSERVED_ON_SITE")));
	}

	@Test
	void thePerformerChangingOwnValues_needsNoCorrection() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		String spaceId = checklists.newId();
		checklists.saved(saleA, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "P101"))));
		checklists.saved(saleA, id, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("30"), "MEASURED")));

		checklists.saved(saleA, id, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("31"), "MEASURED")));
	}

	@Test
	void aStarterWhoDidNotPerform_needsACorrectionToChangeAnOnSiteValueTheyTyped() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		checklists.reassign(leader, visit.leadId(), saleB);
		Result started = checklists.start(saleB, visit.appointmentId(), ChecklistApiTestSupport.intake(
				"Took the lead over", visit.arrivalDate(), "A sent me the notes"));
		assertEquals(201, started.status(), started.rawBody());
		Draft draft = new Draft(visit, started.body().path("checklist").path("id").asString(),
				List.of(checklists.newId()));
		checklists.saved(saleB, draft.id(), "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(draft.space(), "P101"))));
		JsonNode typed = checklists.saved(saleB, draft.id(), "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("30"), "MEASURED")));
		assertEquals(saleA.userId(), area(typed).path("meta").path("observedByUserId").asString());

		assertNeedsCorrection(draft, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("31"), "MEASURED")));
	}

	// ==================== W16-44 ====================

	@Test
	void refillingAClearedValue_andAddingToANoneSeenList_needACorrection() throws Exception {
		Draft draft = handedOver("WHOLE", 1, d -> {
			checklists.saved(saleA, d.id(), "dimensions", c -> set(c, "spaces.0.area",
					checklists.sourced(new BigDecimal("30"), "MEASURED")));
			checklists.saved(saleA, d.id(), "safety", c -> set(c, "hazards", checklists.value(List.of())));
		});

		JsonNode cleared = needsCorrection(draft, "dimensions", c -> set(c, "spaces.0.area",
				checklists.unknown(true)));
		assertEquals("MEASURED", area(cleared).path("meta").path("lastSource").asString());
		assertNeedsCorrection(draft, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("33"), "MEASURED")));
		assertNeedsCorrection(draft, "safety", c -> set(c, "hazards", checklists.value(List.of(
				checklists.hazard(checklists.newId(), "MINOR", "OPEN", null)))));
	}

	@Test
	void addingToANonEmptyHazardList_andRecordingAFixReport_needNoCorrection() throws Exception {
		String hazardId = checklists.newId();
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "safety", c -> set(c, "hazards",
				checklists.value(List.of(checklists.hazard(hazardId, "IMPORTANT", "OPEN", null))))));

		noCorrection(draft, "safety", c -> ((ArrayNode) c.path("hazards").path("value")).add(
				checklists.hazard(checklists.newId(), "MINOR", "OPEN", null)));
		noCorrection(draft, "safety", c -> {
			ObjectNode hazard = (ObjectNode) c.path("hazards").path("value").path(0);
			hazard.put("status", "OWNER_REPORTED_FIXED");
			hazard.put("statusNote", "Owner replaced the railing");
			hazard.put("ownerReportedOn", today.toString());
			hazard.put("receivedByUserId", saleB.userId());
		});
	}

	@Test
	void fixReportsOfA_needACorrection_unlessReplacedByAnotherReceiversDifferentReport() throws Exception {
		String hazardId = checklists.newId();
		LocalDate reported = today.minusDays(1);
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "safety", c -> {
			ObjectNode hazard = checklists.hazard(hazardId, "IMPORTANT", "OWNER_REPORTED_FIXED", "Railing fixed");
			hazard.put("ownerReportedOn", reported.toString());
			hazard.put("receivedByUserId", saleA.userId());
			set(c, "hazards", checklists.value(List.of(hazard)));
		}));

		assertNeedsCorrection(draft, "safety", c -> ((ObjectNode) c.path("hazards").path("value").path(0))
				.put("receivedByUserId", saleB.userId()));
		assertNeedsCorrection(draft, "safety", c -> {
			ObjectNode hazard = (ObjectNode) c.path("hazards").path("value").path(0);
			hazard.put("statusNote", "Railing replaced");
			hazard.put("relayBasis", "A's visit notes");
		});
		assertNeedsCorrection(draft, "safety", c -> {
			ObjectNode hazard = (ObjectNode) c.path("hazards").path("value").path(0);
			hazard.put("status", "OPEN");
			hazard.putNull("statusNote");
			hazard.putNull("ownerReportedOn");
			hazard.putNull("receivedByUserId");
		});
		noCorrection(draft, "safety", c -> {
			ObjectNode hazard = (ObjectNode) c.path("hazards").path("value").path(0);
			hazard.put("statusNote", "Railing replaced");
			hazard.put("receivedByUserId", saleB.userId());
		});
	}

	@Test
	void thePerformerEditingAFixReportSomeoneElseHeard_needsACorrection() throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		Draft draft = new Draft(visit, id, List.of(checklists.newId()));
		checklists.saved(saleA, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(draft.space(), "P101"))));
		String hazardId = checklists.newId();
		checklists.saved(saleA, id, "safety", c -> {
			ObjectNode hazard = checklists.hazard(hazardId, "IMPORTANT", "OWNER_REPORTED_FIXED", "Railing fixed");
			hazard.put("ownerReportedOn", today.toString());
			hazard.put("receivedByUserId", leader.userId());
			hazard.put("relayBasis", "The leader called me");
			set(c, "hazards", checklists.value(List.of(hazard)));
		});

		Result without = checklists.save(saleA, id, "safety", c -> ((ObjectNode) c.path("hazards").path("value")
				.path(0)).put("statusNote", "Railing replaced"));

		assertError(without, 400, "VALIDATION_FAILED", "correction");
	}

	@Test
	void feeListsAndANewOverrideUnderAsCommonCharge_needACorrection() throws Exception {
		Draft draft = handedOver("PARTS", 2, d -> checklists.saved(saleA, d.id(), "terms", c -> {
			ObjectNode fee = checklists.object();
			fee.put("name", "Cleaning");
			fee.set("amount", checklists.money(100_000));
			fee.put("chargeUnit", "PER_MONTH");
			set(c, "common.otherFees", checklists.sourced(List.of(fee), "OBSERVED_ON_SITE"));
			set(c, "common.electricity", checklists.sourced(checklists.charge("AMOUNT", 3_500L, "PER_KWH"),
					"OBSERVED_ON_SITE"));
		}));

		assertNeedsCorrection(draft, "terms", c -> ((ObjectNode) c.path("common").path("otherFees").path("value")
				.path(0)).set("amount", checklists.money(150_000)));
		JsonNode overridden = needsCorrection(draft, "terms", c -> set(c, "spaces.0.chargeOverrides.electricity",
				checklists.sourced(checklists.charge("AMOUNT", 4_000L, "PER_KWH"), "OBSERVED_ON_SITE")));

		String saveId = String.valueOf(jdbc.queryForObject("select id from survey_checklist_saves where checklist_id = "
				+ "? and group_key = 'TERMS' order by saved_at desc, id desc limit 1", Long.class, draft.id()));
		assertEquals(saveId, overridden.path("groups").path("terms").path("content").path("spaces").path(0)
				.path("chargeOverrides").path("electricity").path("meta").path("correctionSaveId").asString());
		List<String> units = new ArrayList<>();
		correctionOf(overridden, saveId).path("units").forEach(unit -> units.add(unit.path("unitKey").asString()));
		assertEquals(List.of("terms/spaces/" + draft.space() + "/chargeOverrides/electricity",
				"terms/common/electricity"), units);
	}

	@Test
	void ownerFormAndStatementRefills_andReceiverSwaps() throws Exception {
		LocalDate stated = today.minusDays(2);
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "terms", c -> {
			set(c, "spaces.0.monthlyRent", checklists.sourced(checklists.rent(12_000_000), "OWNER_FORM"));
			set(c, "spaces.0.deposit", checklists.ownerStated(deposit(2), stated, saleA.userId(), null));
		}));

		noCorrection(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.unknown(true)));
		noCorrection(draft, "terms", c -> set(c, "spaces.0.monthlyRent",
				checklists.sourced(checklists.rent(12_000_000), "OWNER_FORM")));
		assertNeedsCorrection(draft, "terms", c -> set(c, "spaces.0.deposit", checklists.ownerStated(deposit(2),
				stated, saleB.userId(), null)));
		noCorrection(draft, "terms", c -> set(c, "spaces.0.deposit", checklists.ownerStated(deposit(3), stated,
				saleB.userId(), null)));
	}

	@Test
	void hearingTheOwnerRepeatAsValueOnTheSameDay_isSavedWithACorrection() throws Exception {
		LocalDate stated = today.minusDays(1);
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "terms", c -> set(c,
				"spaces.0.deposit", checklists.ownerStated(deposit(2), stated, saleA.userId(), null))));

		JsonNode saved = needsCorrection(draft, "terms", c -> set(c, "spaces.0.deposit",
				checklists.ownerStated(deposit(2), stated, saleB.userId(), null)));

		assertEquals(saleB.userId(), saved.path("groups").path("terms").path("content").path("spaces").path(0)
				.path("deposit").path("meta").path("observedByUserId").asString());
	}

	@Test
	void clearedStatementsOfA_takeANewerStatementFreely_butTheSameDayWithAnotherReceiverNeedsACorrection()
			throws Exception {
		LocalDate stated = today.minusDays(2);
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "terms", c -> set(c,
				"spaces.0.deposit", checklists.ownerStated(deposit(2), stated, saleA.userId(), null))));
		needsCorrection(draft, "terms", c -> set(c, "spaces.0.deposit", checklists.unknown(true)));

		assertNeedsCorrection(draft, "terms", c -> set(c, "spaces.0.deposit", checklists.ownerStated(deposit(1),
				stated, saleB.userId(), null)));
		noCorrection(draft, "terms", c -> set(c, "spaces.0.deposit", checklists.ownerStated(deposit(1),
				stated.plusDays(1), saleB.userId(), null)));
	}

	@Test
	void aSameDayStatementByAnotherReceiver_changingAnItem_needsNoCorrection() throws Exception {
		LocalDate stated = today.minusDays(1);
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "amenities", c ->
				((ArrayNode) c.path("sharedItems")).add(item("WATER_HEATER", "YES", stated, saleA.userId()))));

		noCorrection(draft, "amenities", c -> ((ArrayNode) c.path("sharedItems")).set(0,
				item("WATER_HEATER", "NO", stated, saleB.userId())));
	}

	@Test
	void partsRoundTrip_bringsAsSharedWetAreasBackAsClearedUnits_andRefillsNeedACorrection() throws Exception {
		Draft draft = handedOver("PARTS", 2, d -> {
			checklists.saved(saleA, d.id(), "condition", c -> {
				set(c, "sharedKitchen", checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
				set(c, "sharedBathroom", checklists.notApplicable("No shared bathroom", false));
			});
			checklists.saved(saleA, d.id(), "terms", c -> {
				set(c, "common.electricity", checklists.sourced(checklists.charge("AMOUNT", 3_500L, "PER_KWH"),
						"OBSERVED_ON_SITE"));
				set(c, "spaces.0.chargeOverrides.electricity", checklists.sourced(checklists.charge("AMOUNT",
						4_000L, "PER_KWH"), "OBSERVED_ON_SITE"));
			});
		});

		needsCorrection(draft, "identification", c -> {
			c.put("rentalScope", "WHOLE");
			((ArrayNode) c.path("spaces")).remove(1);
		});
		JsonNode back = needsCorrection(draft, "identification", c -> {
			c.put("rentalScope", "PARTS");
			((ArrayNode) c.path("spaces")).add(checklists.space(checklists.newId(), "P109"));
		});

		JsonNode kitchen = back.path("groups").path("condition").path("content").path("sharedKitchen");
		assertEquals("UNKNOWN", kitchen.path("state").asString());
		assertEquals(saleA.userId(), kitchen.path("meta").path("observedByUserId").asString());
		assertNeedsCorrection(draft, "condition", c -> set(c, "sharedKitchen", checklists.value(
				checklists.areaCheck("INSPECTED", true, null, null))));
		assertNeedsCorrection(draft, "terms", c -> set(c, "spaces.0.chargeOverrides.electricity",
				checklists.sourced(checklists.charge("AMOUNT", 4_200L, "PER_KWH"), "OBSERVED_ON_SITE")));
	}

	@Test
	void editingAsGeneralNote_needsNoCorrection_andShowsItAsTheEditorsNote() throws Exception {
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "identification",
				c -> c.put("generalNote", "Gate code is with the neighbour")));

		JsonNode saved = noCorrection(draft, "identification", c -> c.put("generalNote", "Ask the neighbour"));

		JsonNode meta = saved.path("groups").path("identification").path("content").path("fieldMeta")
				.path("generalNote");
		assertEquals(saleB.userId(), meta.path("enteredByUserId").asString());
		assertTrue(meta.path("observedByUserId").isNull(), meta.toString());
	}

	// ==================== W16-46 (BR-28, BR-29, spec A-29) ====================

	/**
	 * W16-46 whole: G6 identity across removal. A performed the visit; the leader (C) heard the owner about P1's bed
	 * on day d. Every step is a separate request, so each tombstone is stored in and read from PostgreSQL.
	 */
	@Test
	void g6Entries_removedAndSentAgain_keepTheProvenanceTheyHad() throws Exception {
		LocalDate d = today.minusDays(2);
		String x = checklists.newId();
		String y = checklists.newId();
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		Draft draft = new Draft(visit, id, List.of(checklists.newId(), checklists.newId()));
		String p1 = draft.spaces().get(0);
		String p2 = draft.spaces().get(1);
		checklists.saved(saleA, id, "identification", c -> c.setAll(checklists.identification("PARTS",
				checklists.space(p1, "P101"), checklists.space(p2, "P102"))));
		checklists.saved(saleA, id, "condition", c -> set(c, "sharedKitchen", checklists.value(
				checklists.areaCheck("INSPECTED", true, null, null))));
		checklists.saved(saleA, id, "amenities", c -> {
			addShared(c, item("AIR_CONDITIONER", "YES", null, null));
			addShared(c, item("WATER_HEATER", "YES", null, null));
			addShared(c, item("ELEVATOR", "YES", null, null));
			addToSpace(c, 0, statedItem("BED", "YES", d, leader.userId(), "The leader called the owner"));
			addToSpace(c, 1, item("DESK", "YES", null, null));
			addOther(c, otherItem(x, "Water filter", p1));
			addOther(c, otherItem(y, "Shoe rack", p2));
		});
		checklists.saved(saleA, id, "amenities", c -> remove(c.path("sharedItems"), "code", "WATER_HEATER"));
		checklists.saved(saleA, id, "amenities", c -> addShared(c, item("WATER_HEATER", "YES", null, null)));
		checklists.saved(saleA, id, "amenities", c -> remove(c.path("sharedItems"), "code", "WATER_HEATER"));

		checklists.reassign(leader, visit.leadId(), saleB);
		Result takenOver = checklists.takeOver(saleB, id, visit);
		assertEquals(201, takenOver.status(), takenOver.rawBody());
		Result checkpoint = checklists.checkpoint(saleB, id, 1);
		assertEquals(200, checkpoint.status(), checkpoint.rawBody());
		assertEquals(List.of("AIR_CONDITIONER", "ELEVATOR"), codes(checkpoint.body().path("snapshot").path("groups")
				.path("amenities").path("content").path("sharedItems")));
		assertFalse(checkpoint.rawBody().contains("tombstone"), checkpoint.rawBody());

		// A's own removal before the takeover still protects A from B
		assertNeedsCorrection(draft, "amenities", c -> addShared(c, item("WATER_HEATER", "YES", null, null)));

		JsonNode removed = savedAndRetried(saleB, id, "amenities", fix(), c -> remove(c.path("sharedItems"), "code",
				"AIR_CONDITIONER"));
		assertEquals(List.of("ELEVATOR"), codes(sharedItems(removed)));
		assertEquals(List.of("ELEVATOR"), codes(sharedItems(checklists.readOk(saleB, id).body())));
		JsonNode tombstone = storedTombstones(id, "AMENITIES").path("amenities/sharedItems/AIR_CONDITIONER");
		assertEquals(saleA.userId(), tombstone.path("observedByUserId").asString());
		assertEquals(saleB.userId(), tombstone.path("enteredByUserId").asString());
		assertEquals(updatedAt(id, "AMENITIES"), Instant.parse(tombstone.path("enteredAt").asString()));
		assertEquals(latestSaveId(id, "AMENITIES"), tombstone.path("correctionSaveId").asString());

		assertNeedsCorrection(draft, "amenities", c -> addShared(c, item("AIR_CONDITIONER", "YES", null, null)));
		assertNeedsCorrection(draft, "amenities", c -> addShared(c, item("AIR_CONDITIONER", "YES", today,
				saleB.userId())));
		noCorrection(draft, "amenities", c -> addOther(c, otherItem(checklists.newId(), "Curtains", null)));
		assertNeedsCorrection(draft, "amenities", c -> addShared(c, item("AIR_CONDITIONER", "YES", null, null)));
		JsonNode back = savedAndRetried(saleB, id, "amenities", fix(), c -> addShared(c, item("AIR_CONDITIONER",
				"YES", null, null)));
		JsonNode backMeta = sharedItem(back, "AIR_CONDITIONER").path("meta");
		assertEquals(saleA.userId(), backMeta.path("observedByUserId").asString());
		assertEquals(saleB.userId(), backMeta.path("enteredByUserId").asString());
		assertEquals(latestSaveId(id, "AMENITIES"), backMeta.path("correctionSaveId").asString());

		noCorrection(draft, "amenities", c -> addToSpace(c, 0, item("AIR_CONDITIONER", "YES", null, null)));

		needsCorrection(draft, "amenities", c -> remove(c.path("spaces").path(0).path("items"), "code", "BED"));
		assertNeedsCorrection(draft, "amenities", c -> addToSpace(c, 0, item("BED", "NO", d, saleB.userId())));
		noCorrection(draft, "amenities", c -> addToSpace(c, 0, item("BED", "YES", d.plusDays(1), saleB.userId())));

		needsCorrection(draft, "amenities", c -> remove(c.path("otherItems"), "itemId", x));
		assertNeedsCorrection(draft, "amenities", c -> addOther(c, otherItem(x, "Water filter", p1)));
		assertNeedsCorrection(draft, "amenities", c -> addOther(c, otherItem(x, "RO water filter", p2)));
		noCorrection(draft, "amenities", c -> addOther(c, otherItem(checklists.newId(), "Water filter", p1)));

		needsCorrection(draft, "amenities", c -> remove(c.path("spaces").path(1).path("items"), "code", "DESK"));
		assertTrue(storedTombstones(id, "AMENITIES").has("amenities/spaces/" + p2 + "/items/DESK"));
		needsCorrection(draft, "identification", c -> ((ArrayNode) c.path("spaces")).remove(1));
		JsonNode afterP2 = storedTombstones(id, "AMENITIES");
		assertEquals(saleB.userId(), afterP2.path("amenities/otherItems/" + y).path("enteredByUserId").asString());
		assertEquals(saleA.userId(), afterP2.path("amenities/otherItems/" + y).path("observedByUserId").asString());
		assertFalse(afterP2.toString().contains(p2), afterP2.toString());
		assertNeedsCorrection(draft, "amenities", c -> addOther(c, otherItem(y, "Shoe rack", null)));

		needsCorrection(draft, "identification", c -> c.put("rentalScope", "WHOLE"));
		JsonNode roundTrip = needsCorrection(draft, "identification", c -> c.put("rentalScope", "PARTS"));
		JsonNode kitchen = roundTrip.path("groups").path("condition").path("content").path("sharedKitchen");
		assertEquals("UNKNOWN", kitchen.path("state").asString());
		assertEquals(saleA.userId(), kitchen.path("meta").path("observedByUserId").asString());
		assertEquals(saleB.userId(), kitchen.path("meta").path("enteredByUserId").asString());
		assertEquals(saleA.userId(), sharedItem(roundTrip, "ELEVATOR").path("meta").path("enteredByUserId")
				.asString());

		checklists.reassign(leader, visit.leadId(), saleA);
		Result backToA = checklists.takeOver(saleA, id, visit);
		assertEquals(201, backToA.status(), backToA.rawBody());
		assertError(checklists.save(saleA, id, "amenities", c -> addOther(c, otherItem(x, "Water filter", p1))), 400,
				"VALIDATION_FAILED", "correction");
		assertError(checklists.save(saleA, id, "condition", c -> set(c, "sharedKitchen", checklists.value(
				checklists.areaCheck("INSPECTED", true, null, null)))), 400, "VALIDATION_FAILED", "correction");
	}

	/**
	 * Review Focus 6: B removes A's air conditioner and saves; after an unrelated save B sends it again and needs a
	 * correction, and so does A when the lead comes back to A (BR-29).
	 */
	@Test
	void amenities_removeSaveReaddSameCode_needsCorrection() throws Exception {
		Draft draft = handedOver("WHOLE", 1, d -> checklists.saved(saleA, d.id(), "amenities", c -> addShared(c,
				item("AIR_CONDITIONER", "YES", null, null))));

		needsCorrection(draft, "amenities", c -> remove(c.path("sharedItems"), "code", "AIR_CONDITIONER"));
		noCorrection(draft, "amenities", c -> addOther(c, otherItem(checklists.newId(), "Curtains", null)));
		assertNeedsCorrection(draft, "amenities", c -> addShared(c, item("AIR_CONDITIONER", "YES", null, null)));

		checklists.reassign(leader, draft.visit().leadId(), saleA);
		Result backToA = checklists.takeOver(saleA, draft.id(), draft.visit());
		assertEquals(201, backToA.status(), backToA.rawBody());
		assertError(checklists.save(saleA, draft.id(), "amenities", c -> addShared(c, item("AIR_CONDITIONER", "YES",
				null, null))), 400, "VALIDATION_FAILED", "correction");
	}

	// ==================== helpers ====================

	/** A visits and starts, saves G1 with the scope and spaces, runs {@code prepare}; B then takes over. */
	private Draft handedOver(String scope, int spaceCount, Prepare prepare) throws Exception {
		Visit visit = checklists.visited(leader, saleA);
		String id = checklists.started(saleA, visit.appointmentId());
		List<String> spaces = new ArrayList<>();
		ObjectNode[] nodes = new ObjectNode[spaceCount];
		for (int i = 0; i < spaceCount; i++) {
			spaces.add(checklists.newId());
			nodes[i] = checklists.space(spaces.get(i), "P10" + (i + 1));
		}
		checklists.saved(saleA, id, "identification", c -> c.setAll(checklists.identification(scope, nodes)));
		Draft draft = new Draft(visit, id, spaces);
		prepare.run(draft);
		checklists.reassign(leader, visit.leadId(), saleB);
		Result takenOver = checklists.takeOver(saleB, id, visit);
		assertEquals(201, takenOver.status(), takenOver.rawBody());
		return draft;
	}

	/** B's edit: 400 on {@code correction} without one, 200 with one; returns the corrected checklist. */
	private JsonNode needsCorrection(Draft draft, String group, Consumer<ObjectNode> edit) throws Exception {
		assertNeedsCorrection(draft, group, edit);
		return checklists.saved(saleB, draft.id(), group, ChecklistApiTestSupport.correction("Measured again",
				today, "Checked with the owner"), edit);
	}

	private void assertNeedsCorrection(Draft draft, String group, Consumer<ObjectNode> edit) throws Exception {
		assertError(checklists.save(saleB, draft.id(), group, edit), 400, "VALIDATION_FAILED", "correction");
	}

	private JsonNode noCorrection(Draft draft, String group, Consumer<ObjectNode> edit) throws Exception {
		return checklists.saved(saleB, draft.id(), group, edit);
	}

	private ObjectNode item(String code, String presence, LocalDate statedOrNull, String receiverOrNull) {
		ObjectNode item = checklists.object();
		item.put("code", code);
		item.put("presence", presence);
		item.put("source", statedOrNull == null ? "OBSERVED_ON_SITE" : "OWNER_STATED");
		item.put("confirmedOn", statedOrNull == null ? null : statedOrNull.toString());
		item.put("receivedByUserId", receiverOrNull);
		item.putNull("relayBasis");
		return item;
	}

	/** An owner statement of the item heard by someone else than the person entering it. */
	private ObjectNode statedItem(String code, String presence, LocalDate stated, String receiver, String relayBasis) {
		ObjectNode item = item(code, presence, stated, receiver);
		item.put("relayBasis", relayBasis);
		return item;
	}

	private ObjectNode otherItem(String itemId, String label, String spaceIdOrNull) {
		ObjectNode item = checklists.object();
		item.put("itemId", itemId);
		item.put("label", label);
		item.put("spaceId", spaceIdOrNull);
		item.put("presence", "YES");
		item.put("source", "OBSERVED_ON_SITE");
		item.putNull("confirmedOn");
		item.putNull("receivedByUserId");
		item.putNull("relayBasis");
		return item;
	}

	private static void addShared(ObjectNode content, ObjectNode item) {
		((ArrayNode) content.path("sharedItems")).add(item);
	}

	/** Adds the item to the space at that index of the per-space list (one entry per G1 space, G1 order). */
	private static void addToSpace(ObjectNode content, int space, ObjectNode item) {
		((ArrayNode) content.path("spaces").path(space).path("items")).add(item);
	}

	private static void addOther(ObjectNode content, ObjectNode item) {
		((ArrayNode) content.path("otherItems")).add(item);
	}

	/** Removes the entries whose field has the value. */
	private static void remove(JsonNode list, String field, String value) {
		ArrayNode array = (ArrayNode) list;
		for (int i = array.size() - 1; i >= 0; i--) {
			if (value.equals(array.path(i).path(field).asString())) {
				array.remove(i);
			}
		}
	}

	private static JsonNode sharedItems(JsonNode checklist) {
		return checklist.path("groups").path("amenities").path("content").path("sharedItems");
	}

	private static JsonNode sharedItem(JsonNode checklist, String code) {
		for (JsonNode item : sharedItems(checklist)) {
			if (code.equals(item.path("code").asString())) {
				return item;
			}
		}
		throw new AssertionError("no shared item " + code);
	}

	private static List<String> codes(JsonNode items) {
		List<String> codes = new ArrayList<>();
		items.forEach(item -> codes.add(item.path("code").asString()));
		return codes;
	}

	private Map<String, Object> fix() {
		return ChecklistApiTestSupport.correction("Measured again", today, "Checked with the owner");
	}

	/**
	 * Saves through a read as the caller and at once sends the identical body again: the retry answers 200 as a no-op
	 * (spec A-17) with no save-log or History row, and the stored group, tombstones included, stays as it was.
	 */
	private JsonNode savedAndRetried(TestUser caller, String id, String group, Map<String, Object> correctionOrNull,
			Consumer<ObjectNode> edit) throws Exception {
		JsonNode read = checklists.readOk(caller, id).body();
		ObjectNode content = ChecklistApiTestSupport.input(read, group);
		edit.accept(content);
		long version = ChecklistApiTestSupport.version(read, group);
		Result saved = checklists.put(caller, id, group, version, content, correctionOrNull, null);
		assertEquals(200, saved.status(), saved.rawBody());
		int rows = checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ?", id);
		int history = checklists.rows("select count(*) from owner_lead_activities where reference_id = ?", id);
		String stored = storedContent(id, group.toUpperCase());

		Result retried = checklists.put(caller, id, group, version, content, correctionOrNull, null);

		assertEquals(200, retried.status(), retried.rawBody());
		assertEquals(rows, checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ?", id));
		assertEquals(history, checklists.rows("select count(*) from owner_lead_activities where reference_id = ?",
				id));
		assertEquals(stored, storedContent(id, group.toUpperCase()));
		return saved.body();
	}

	private String storedContent(String checklistId, String groupKey) {
		return jdbc.queryForObject("select content from survey_checklist_groups where checklist_id = ? and group_key = ?",
				String.class, checklistId, groupKey);
	}

	/** The tombstones of the stored group document (server-only, spec A-29), read from PostgreSQL. */
	private JsonNode storedTombstones(String checklistId, String groupKey) {
		return checklists.api().json().readTree(storedContent(checklistId, groupKey)).path("tombstones");
	}

	private Instant updatedAt(String checklistId, String groupKey) {
		return jdbc.queryForObject("select updated_at from survey_checklist_groups where checklist_id = ? and "
				+ "group_key = ?", Timestamp.class, checklistId, groupKey).toInstant();
	}

	private String latestSaveId(String checklistId, String groupKey) {
		return String.valueOf(jdbc.queryForObject("select id from survey_checklist_saves where checklist_id = ? and "
				+ "group_key = ? order by saved_at desc, id desc limit 1", Long.class, checklistId, groupKey));
	}

	private ObjectNode deposit(int months) {
		ObjectNode deposit = checklists.object();
		deposit.put("basis", "MONTHS");
		deposit.put("months", months);
		deposit.putNull("amount");
		return deposit;
	}

	private static JsonNode area(JsonNode checklist) {
		return checklist.path("groups").path("dimensions").path("content").path("spaces").path(0).path("area");
	}

	private static JsonNode correctionOf(JsonNode checklist, String saveId) {
		for (JsonNode correction : checklist.path("corrections")) {
			if (saveId.equals(correction.path("saveId").asString())) {
				return correction;
			}
		}
		throw new AssertionError("no correction " + saveId + " in " + checklist.path("corrections"));
	}
}
