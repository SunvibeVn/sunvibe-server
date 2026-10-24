package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Group saves end to end (survey-checklist spec 5.5, 7.1; W16-5..W16-14, W16-18, W16-21 for the PUTs, W16-35, W16-37,
 * W16-38 for correction dates, W16-40, W16-42, and the API cases of the plan's Review Focus 1 and 4). The caller is the
 * performing Sale unless a test says otherwise, so no save here needs a correction. Needs Docker.
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
class ChecklistSaveApiPostgresTest {

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
	private AcquisitionApiTestSupport api;
	private TestUser sale;
	private TestUser saleB;
	private TestUser otherSale;
	private TestUser leader;

	/** A visited lead, its draft and the G1 space ids in order. */
	private record Draft(Visit visit, String id, List<String> spaces) {

		String space() {
			return spaces.get(0);
		}
	}

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0964");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		sale = survey.user("Save Sale", "SALE");
		saleB = survey.user("Save Sale B", "SALE");
		otherSale = survey.user("Save Other Sale", "SALE");
		leader = survey.user("Save Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== W16-5 ====================

	@Test
	void dimensions_save_storesValuesMetaAndLogs_andTheSameBodyAgainIsANoOp() throws Exception {
		Draft draft = draft("WHOLE", 1);
		JsonNode before = checklists.readOk(sale, draft.id()).body();
		long leadVersion = survey.version(draft.visit().leadId());
		long checklistVersion = checklists.checklistVersion(draft.id());
		int logs = checklists.accessLogs(draft.visit().leadId()).size();
		ObjectNode content = ChecklistApiTestSupport.input(before, "dimensions");
		set(content, "spaces.0.area", checklists.sourced(new BigDecimal("35.5"), "MEASURED"));
		set(content, "spaces.0.bedrooms", checklists.sourced(1, "OBSERVED_ON_SITE"));

		Result saved = checklists.put(sale, draft.id(), "dimensions", 0, content, null, null);

		assertEquals(200, saved.status(), saved.rawBody());
		JsonNode groups = saved.body().path("groups");
		assertEquals(1, groups.path("dimensions").path("version").asLong());
		for (String group : List.of("identification", "parts", "condition", "safety", "amenities", "terms")) {
			assertEquals(ChecklistApiTestSupport.version(before, group), groups.path(group).path("version").asLong(),
					group);
		}
		JsonNode area = groups.path("dimensions").path("content").path("spaces").path(0).path("area");
		assertEquals(0, new BigDecimal("35.5").compareTo(area.path("value").decimalValue()));
		assertEquals("MEASURED", area.path("source").asString());
		JsonNode meta = area.path("meta");
		assertEquals(sale.userId(), meta.path("enteredByUserId").asString());
		assertEquals("Save Sale", meta.path("enteredByName").asString());
		assertEquals(sale.userId(), meta.path("observedByUserId").asString());
		assertEquals(draft.visit().arrivalDate().toString(), meta.path("observedOn").asString());
		assertEquals("MEASURED", meta.path("lastSource").asString());
		assertTrue(groups.path("dimensions").path("content").path("spaces").path(0).path("privateBathrooms")
				.path("meta").isNull());
		assertEquals(sale.userId(), groups.path("dimensions").path("updatedByUserId").asString());
		assertEquals(1, saves(draft.id(), "DIMENSIONS"));
		List<Map<String, Object>> history = checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED");
		assertEquals(2, history.size());
		assertEquals("DIMENSIONS", history.get(1).get("reference_detail"));
		assertEquals(draft.id(), history.get(1).get("reference_id"));
		assertEquals(null, history.get(1).get("note"));
		assertEquals(logs + 1, checklists.accessLogs(draft.visit().leadId()).size());
		assertEquals("CHECKLIST:" + draft.id(), last(checklists.accessLogs(draft.visit().leadId())));
		assertEquals(leadVersion, survey.version(draft.visit().leadId()));
		assertEquals(checklistVersion, checklists.checklistVersion(draft.id()));

		Result again = checklists.put(sale, draft.id(), "dimensions", 0, content, null, null);

		assertEquals(200, again.status(), again.rawBody());
		assertEquals(1, again.body().path("groups").path("dimensions").path("version").asLong());
		assertEquals(1, saves(draft.id(), "DIMENSIONS"));
		assertEquals(2, checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED").size());
		assertEquals(logs + 2, checklists.accessLogs(draft.visit().leadId()).size());
	}

	// ==================== W16-6 ====================

	@Test
	void bodyRules_answer400OnTheJsonPath_inEveryGroup_andStoreNothing() throws Exception {
		Draft draft = draft("WHOLE", 1);
		Map<String, Long> before = groupVersions(draft.id());
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);
		String hazardId = checklists.newId();

		assertRefused(draft, "identification", c -> set(c, "observedAddress.source", null),
				"content.observedAddress.source");
		assertRefused(draft, "identification", c -> ((ArrayNode) c.path("spaces")).add(
				checklists.space(checklists.newId(), "P102")), "content.spaces");
		assertRefused(draft, "identification", c -> c.put("generalNote", "Owner phone 0905 123 456"),
				"content.generalNote");
		assertRefused(draft, "identification", c -> set(c, "spaces.0.spaceId", checklists.tree("P101")),
				"content.spaces.0.spaceId");
		assertRefused(draft, "parts", c -> set(c, "accessRoute", checklists.notApplicable("No route", false)),
				"content.accessRoute.state");
		assertRefused(draft, "parts", c -> set(c, "totalFloors", checklists.sourced(201, "OBSERVED_ON_SITE")),
				"content.totalFloors.value");
		assertRefused(draft, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("35.55"), "MEASURED")), "content.spaces.0.area.value");
		assertRefused(draft, "dimensions", c -> set(c, "spaces.0.bedrooms", checklists.sourced(2, "MEASURED")),
				"content.spaces.0.bedrooms.source");
		assertRefused(draft, "dimensions", c -> {
			ObjectNode stated = checklists.ownerStated(2, today, sale.userId(), null);
			stated.putNull("confirmedOn");
			set(c, "spaces.0.bedrooms", stated);
		}, "content.spaces.0.bedrooms.confirmedOn");
		assertRefused(draft, "dimensions", c -> {
			ObjectNode wrapper = checklists.unknown(true);
			wrapper.set("value", checklists.tree(2));
			set(c, "spaces.0.bedrooms", wrapper);
		}, "content.spaces.0.bedrooms.value");
		assertRefused(draft, "condition", c -> set(c, "spaces.0.mainSpace",
				checklists.notApplicable("No main room", false)), "content.spaces.0.mainSpace.state");
		assertRefused(draft, "condition", c -> set(c, "spaces.0.kitchen",
				checklists.value(checklists.areaCheck("NOT_ENTERED", null, null, null))),
				"content.spaces.0.kitchen.value.notEnteredReason");
		assertRefused(draft, "safety", c -> {
			ArrayNode hazards = checklists.object().arrayNode();
			for (int i = 0; i < 31; i++) {
				hazards.add(checklists.hazard(checklists.newId(), "MINOR", "OPEN", null));
			}
			set(c, "hazards", checklists.value(hazards));
		}, "content.hazards.value");
		assertRefused(draft, "safety", c -> {
			ObjectNode hazard = checklists.hazard(hazardId, "MINOR", "OPEN", null);
			hazard.put("description", "Call the owner on 0905 123 456");
			set(c, "hazards", checklists.value(List.of(hazard)));
		}, "content.hazards.value.0.description");
		assertRefused(draft, "amenities", c -> {
			ArrayNode items = (ArrayNode) c.path("sharedItems");
			items.add(item("ELEVATOR", "YES"));
			items.add(item("ELEVATOR", "NO"));
		}, "content.sharedItems.1.code");
		assertRefused(draft, "terms", c -> set(c, "spaces.0.monthlyRent",
				checklists.sourced(checklists.rent(0), "OBSERVED_ON_SITE")), "content.spaces.0.monthlyRent.value.amount");
		assertRefused(draft, "terms", c -> set(c, "spaces.0.deposit", checklists.sourced(deposit("MONTHS", 1, 0L),
				"OBSERVED_ON_SITE")), "content.spaces.0.deposit.value.amount");
		assertRefused(draft, "terms", c -> set(c, "spaces.0.paymentPeriodMonths", checklists.sourced(1,
				"OWNER_FORM")), "content.spaces.0.paymentPeriodMonths.source");
		assertRefused(draft, "terms", c -> set(c, "common.electricity", checklists.sourced(
				checklists.charge("AMOUNT", 3500L, null), "OBSERVED_ON_SITE")),
				"content.common.electricity.value.chargeUnit");
		assertRefused(draft, "terms", c -> {
			ObjectNode rent = checklists.sourced(checklists.rent(12_000_000), "OBSERVED_ON_SITE");
			rent.put("relayBasis", "Heard it");
			set(c, "spaces.0.monthlyRent", rent);
		}, "content.spaces.0.monthlyRent.relayBasis");

		assertEquals(before, groupVersions(draft.id()));
		assertEquals(1, checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED").size());
	}

	/** survey-checklist spec 7.1 (amounts and counts are JSON integers), A-4: a fraction is refused, never cut. */
	@Test
	void fractionalAmountsCountsAndVersions_are400_andStoreNothing() throws Exception {
		Draft draft = draft("WHOLE", 1);
		Map<String, Long> before = groupVersions(draft.id());

		assertRefused(draft, "terms", c -> {
			ObjectNode rent = checklists.rent(12_000_000);
			rent.put("amount", new BigDecimal("12000000.9"));
			set(c, "spaces.0.monthlyRent", checklists.sourced(rent, "OBSERVED_ON_SITE"));
		}, "content.spaces.0.monthlyRent.value.amount");
		assertRefused(draft, "dimensions", c -> set(c, "spaces.0.bedrooms",
				checklists.sourced(new BigDecimal("2.5"), "OBSERVED_ON_SITE")), "content.spaces.0.bedrooms.value");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", new BigDecimal("0.5"));
		body.put("correction", null);
		body.put("content", ChecklistApiTestSupport.input(checklists.readOk(sale, draft.id()).body(), "parts"));
		assertError(api.put(sale, ChecklistApiTestSupport.CHECKLISTS + "/" + draft.id() + "/parts", body), 400,
				"VALIDATION_FAILED", "expectedVersion");

		assertEquals(before, groupVersions(draft.id()));
	}

	@Test
	void save_twoViolations_bothReported() throws Exception {
		Draft draft = draft("WHOLE", 1);

		Result result = checklists.save(sale, draft.id(), "parts", c -> {
			set(c, "accessRoute", checklists.notApplicable("No route", false));
			set(c, "totalFloors", checklists.sourced(0, "OBSERVED_ON_SITE"));
		});

		assertEquals(400, result.status(), result.rawBody());
		assertEquals(List.of("content.accessRoute.state", "content.totalFloors.value"), fields(result));
	}

	// ==================== W16-7 ====================

	@Test
	void ownerStatementDates_beforeTheLeadAreAccepted_futureAndBefore2000AreRefused() throws Exception {
		Draft draft = draft("WHOLE", 1);
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);
		Instant before = Instant.now().minusSeconds(5);

		JsonNode saved = checklists.saved(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.bedrooms",
				checklists.ownerStated(2, today.minusYears(3), sale.userId(), null)));

		JsonNode meta = saved.path("groups").path("dimensions").path("content").path("spaces").path(0)
				.path("bedrooms").path("meta");
		assertEquals(today.minusYears(3).toString(), meta.path("observedOn").asString());
		assertTrue(Instant.parse(meta.path("enteredAt").asString()).isAfter(before));
		assertRefused(draft, "dimensions", c -> set(c, "spaces.0.bedrooms", checklists.ownerStated(2,
				today.plusDays(1), sale.userId(), null)), "content.spaces.0.bedrooms.confirmedOn");
		assertRefused(draft, "dimensions", c -> set(c, "spaces.0.bedrooms", checklists.ownerStated(2,
				LocalDate.of(1999, 12, 31), sale.userId(), null)), "content.spaces.0.bedrooms.confirmedOn");
	}

	// ==================== W16-8 ====================

	@Test
	void identificationAddress_catalogAndPinRules_andBothDifferenceFlags() throws Exception {
		Draft draft = draft(null, 0);
		String spaceId = checklists.newId();
		String leadAddress = jdbc.queryForObject("select address_detail from owner_leads where id = ?", String.class,
				draft.visit().leadId());
		Map<String, Object> leadRow = jdbc.queryForMap("select * from owner_leads where id = ?",
				draft.visit().leadId());

		assertRefused(draft, "identification", c -> {
			c.setAll(checklists.identification("WHOLE", checklists.space(spaceId, "P101")));
			set(c, "observedAddress.value.wardCode", checklists.tree("99999"));
		}, "content.observedAddress.value.wardCode");
		assertRefused(draft, "identification", c -> {
			c.setAll(checklists.identification("WHOLE", checklists.space(spaceId, "P101")));
			set(c, "observedAddress.value", checklists.place("1 Tràng Tiền", SurveyApiTestSupport.WARD,
					new BigDecimal("21.0245"), new BigDecimal("105.8412")));
		}, "content.observedAddress.value.latitude");
		assertRefused(draft, "identification", c -> {
			c.setAll(checklists.identification("WHOLE", checklists.space(spaceId, "P101")));
			set(c, "observedAddress.value", checklists.place("Đảo Hoàng Sa",
					SurveyApiTestSupport.SPECIAL_ZONE_WARD, new BigDecimal("16.5"), new BigDecimal("112.0")));
		}, "content.observedAddress.value.latitude");
		Result otherProvince = checklists.save(sale, draft.id(), "identification", c -> {
			c.setAll(checklists.identification("WHOLE", checklists.space(spaceId, "P101")));
			set(c, "observedAddress.value.provinceCode", checklists.tree("01"));
		});
		assertEquals(400, otherProvince.status(), otherProvince.rawBody());
		assertTrue(fields(otherProvince).get(0).startsWith("content.observedAddress.value."),
				otherProvince.rawBody());

		JsonNode elsewhere = checklists.saved(sale, draft.id(), "identification", c -> c.setAll(
				checklists.identification("WHOLE", checklists.space(spaceId, "P101"))));
		JsonNode identification = elsewhere.path("groups").path("identification").path("content");
		assertTrue(identification.path("difference").path("fromLead").asBoolean());
		assertTrue(identification.path("difference").path("fromMeetingPlace").asBoolean());
		assertEquals("Hải Châu", identification.path("wardName").asString());
		assertEquals(elsewhere.path("visit").path("leadLocation").path("provinceName").asString(),
				identification.path("provinceName").asString());

		JsonNode same = checklists.saved(sale, draft.id(), "identification", c -> set(c, "observedAddress.value",
				checklists.place(leadAddress, SurveyApiTestSupport.WARD, (BigDecimal) leadRow.get("latitude"),
						(BigDecimal) leadRow.get("longitude"))));
		JsonNode difference = same.path("groups").path("identification").path("content").path("difference");
		assertFalse(difference.path("fromLead").asBoolean(), difference.toString());
		assertFalse(difference.path("fromMeetingPlace").asBoolean(), difference.toString());
		assertEquals(leadRow, jdbc.queryForMap("select * from owner_leads where id = ?", draft.visit().leadId()));
	}

	// ==================== W16-9 ====================

	@Test
	void spaces_wholeNeedsOne_codesAreUniqueIgnoringCase_andGroupsNameOnlyKnownSpaces() throws Exception {
		Draft draft = draft(null, 0);
		String a = checklists.newId();
		String b = checklists.newId();

		assertRefused(draft, "identification", c -> c.setAll(checklists.identification("WHOLE")), "content.spaces");
		assertRefused(draft, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(a, "P101"), checklists.space(b, "P102"))), "content.spaces");
		assertRefused(draft, "identification", c -> c.setAll(checklists.identification("PARTS",
				checklists.space(a, "P101"), checklists.space(b, "p101"))), "content.spaces.1.code");
		checklists.saved(sale, draft.id(), "identification", c -> c.setAll(checklists.identification("PARTS",
				checklists.space(a, "P101"))));
		assertRefused(draft, "dimensions", c -> set(c, "spaces.0.spaceId", checklists.tree(b)),
				"content.spaces.0.spaceId");

		checklists.saved(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("20"), "MEASURED")));
		JsonNode added = checklists.saved(sale, draft.id(), "identification", c -> ((ArrayNode) c.path("spaces"))
				.add(checklists.space(b, "P102")));

		JsonNode spaces = added.path("groups").path("dimensions").path("content").path("spaces");
		assertEquals(List.of(a, b), List.of(spaces.path(0).path("spaceId").asString(),
				spaces.path(1).path("spaceId").asString()));
		assertEquals("VALUE", spaces.path(0).path("area").path("state").asString());
		for (String field : List.of("area", "bedrooms", "privateBathrooms", "sharedBathroomUse")) {
			assertEquals("UNKNOWN", spaces.path(1).path(field).path("state").asString(), field);
		}
		assertEquals("UNKNOWN", added.path("groups").path("terms").path("content").path("spaces").path(1)
				.path("monthlyRent").path("state").asString());
	}

	@Test
	void parts_entryForUnknownSpace_is400() throws Exception {
		Draft draft = draft("WHOLE", 1);

		assertRefused(draft, "parts", c -> {
			ObjectNode entry = (ObjectNode) c.path("spaces").path(0).deepCopy();
			entry.put("spaceId", checklists.newId());
			((ArrayNode) c.path("spaces")).add(entry);
		}, "content.spaces.1.spaceId");
	}

	@Test
	void spaceRemoval_cascadesIntoTheGroupsHoldingIt_andAStaleOtherVersionRemovesNothing() throws Exception {
		Draft draft = draft("PARTS", 2);
		String removed = draft.spaces().get(1);
		checklists.saved(sale, draft.id(), "dimensions", c -> {
			set(c, "spaces.0.area", checklists.sourced(new BigDecimal("20"), "MEASURED"));
			set(c, "spaces.1.area", checklists.sourced(new BigDecimal("25"), "MEASURED"));
		});
		String hazardId = checklists.newId();
		checklists.saved(sale, draft.id(), "safety", c -> {
			ObjectNode hazard = checklists.hazard(hazardId, "MINOR", "OPEN", null);
			hazard.put("spaceId", removed);
			set(c, "hazards", checklists.value(List.of(hazard)));
		});
		JsonNode read = checklists.readOk(sale, draft.id()).body();
		ObjectNode g1 = ChecklistApiTestSupport.input(read, "identification");
		((ArrayNode) g1.path("spaces")).remove(1);
		Map<String, Object> stale = ChecklistApiTestSupport.otherVersions(read);
		stale.put("dimensions", ChecklistApiTestSupport.version(read, "dimensions") - 1);

		Result refused = checklists.put(sale, draft.id(), "identification",
				ChecklistApiTestSupport.version(read, "identification"), g1, null, stale);

		assertError(refused, 409, "CONCURRENT_MODIFICATION", "otherGroupVersions.dimensions");
		assertEquals(2, checklists.readOk(sale, draft.id()).body().path("groups").path("dimensions")
				.path("content").path("spaces").size());

		JsonNode removedSpace = checklists.saved(sale, draft.id(), "identification",
				c -> ((ArrayNode) c.path("spaces")).remove(1));

		JsonNode groups = removedSpace.path("groups");
		assertEquals(1, groups.path("dimensions").path("content").path("spaces").size());
		assertEquals(ChecklistApiTestSupport.version(read, "dimensions") + 1,
				groups.path("dimensions").path("version").asLong());
		assertEquals(ChecklistApiTestSupport.version(read, "safety") + 1, groups.path("safety").path("version")
				.asLong());
		assertTrue(groups.path("safety").path("content").path("hazards").path("value").path(0).path("spaceId")
				.isNull());
		assertEquals(ChecklistApiTestSupport.version(read, "parts"), groups.path("parts").path("version").asLong());
		assertEquals(2, saves(draft.id(), "SAFETY"));
		assertEquals(draft.spaces().subList(0, 1), List.of(groups.path("identification").path("content")
				.path("spaces").path(0).path("spaceId").asString()));
	}

	@Test
	void identification_renameSpace_keepsEntries() throws Exception {
		Draft draft = draft("WHOLE", 1);
		checklists.saved(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("30"), "MEASURED")));
		long dimensionsVersion = groupVersions(draft.id()).get("DIMENSIONS");

		JsonNode renamed = checklists.saved(sale, draft.id(), "identification", c -> set(c, "spaces.0.code",
				checklists.tree("A1")));

		assertEquals("A1", renamed.path("groups").path("identification").path("content").path("spaces").path(0)
				.path("code").asString());
		JsonNode area = renamed.path("groups").path("dimensions").path("content").path("spaces").path(0)
				.path("area");
		assertEquals("VALUE", area.path("state").asString());
		assertEquals(sale.userId(), area.path("meta").path("observedByUserId").asString());
		assertEquals(dimensionsVersion, groupVersions(draft.id()).get("DIMENSIONS"));
	}

	// ==================== W16-10 ====================

	@Test
	void condition_areaChecks_needReasonsAndStayMissingWhenNotEntered() throws Exception {
		Draft draft = draft("WHOLE", 1);

		assertRefused(draft, "condition", c -> set(c, "sharedKitchen", checklists.value(checklists.areaCheck(
				"INSPECTED", true, null, null))), "content.sharedKitchen");
		JsonNode saved = checklists.saved(sale, draft.id(), "condition", c -> set(c, "spaces.0.kitchen",
				checklists.value(checklists.areaCheck("NOT_ENTERED", null, "Owner says it was redone",
						"The kitchen was locked"))));

		assertTrue(missing(saved).contains("CONDITION/" + draft.space() + "/kitchen/REQUIRED_AREA_NOT_ENTERED"),
				missing(saved).toString());
		assertTrue(saved.path("groups").path("condition").path("content").path("sharedKitchen").isNull());
	}

	@Test
	void condition_partsNullSharedKitchen_is400_andUnknownSharedAreasAreMissing() throws Exception {
		Draft draft = draft("PARTS", 2);

		assertRefused(draft, "condition", c -> c.putNull("sharedKitchen"), "content.sharedKitchen");
		JsonNode read = checklists.readOk(sale, draft.id()).body();
		assertEquals("UNKNOWN", read.path("groups").path("condition").path("content").path("sharedKitchen")
				.path("state").asString());
		assertTrue(missing(read).contains("CONDITION/-/sharedKitchen/NOT_CHECKED"), missing(read).toString());

		JsonNode inspected = checklists.saved(sale, draft.id(), "condition", c -> {
			set(c, "sharedKitchen", checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
			set(c, "sharedBathroom", checklists.notApplicable("No shared bathroom", false));
		});
		assertFalse(missing(inspected).contains("CONDITION/-/sharedKitchen/NOT_CHECKED"));
		assertFalse(missing(inspected).contains("CONDITION/-/sharedBathroom/NOT_CHECKED"));
	}

	@Test
	void condition_partsResentWithStaleVersion_isNoOp() throws Exception {
		Draft draft = draft("PARTS", 2);
		JsonNode saved = checklists.saved(sale, draft.id(), "condition", c -> set(c, "spaces.0.occupancy",
				checklists.value("VACANT")));
		ObjectNode resent = ChecklistApiTestSupport.input(saved, "condition");
		int rows = saves(draft.id(), "CONDITION");

		Result again = checklists.put(sale, draft.id(), "condition", 0, resent, null, null);

		assertEquals(200, again.status(), again.rawBody());
		assertEquals(1, again.body().path("groups").path("condition").path("version").asLong());
		assertEquals(rows, saves(draft.id(), "CONDITION"));
	}

	// ==================== W16-11 ====================

	@Test
	void terms_zeroDepositMeansNone_overridesAndUnconfirmedCommonCharges() throws Exception {
		Draft whole = draft("WHOLE", 1);
		checklists.saved(sale, whole.id(), "terms", c -> set(c, "spaces.0.deposit", checklists.sourced(
				deposit("MONTHS", 0, null), "OBSERVED_ON_SITE")));
		checklists.saved(sale, whole.id(), "terms", c -> set(c, "spaces.0.deposit", checklists.sourced(
				deposit("AMOUNT", null, 0L), "OBSERVED_ON_SITE")));

		Draft parts = draft("PARTS", 2);
		JsonNode saved = checklists.saved(sale, parts.id(), "terms", c -> {
			set(c, "common.electricity", checklists.sourced(checklists.charge("AMOUNT", 3_500L, "PER_KWH"),
					"OBSERVED_ON_SITE"));
			set(c, "spaces.0.chargeOverrides.electricity", checklists.sourced(checklists.charge("AMOUNT", 4_000L,
					"PER_KWH"), "OBSERVED_ON_SITE"));
		});

		JsonNode effective = saved.path("effectiveTerms");
		assertEquals("SPACE", effective.path(0).path("electricity").path("origin").asString());
		assertEquals(4_000, effective.path(0).path("electricity").path("term").path("value").path("amount")
				.path("amount").asLong());
		assertEquals("COMMON", effective.path(1).path("electricity").path("origin").asString());
		assertEquals(3_500, effective.path(1).path("electricity").path("term").path("value").path("amount")
				.path("amount").asLong());
		assertTrue(unconfirmed(saved).contains("TERMS/-/water"), unconfirmed(saved).toString());

		JsonNode overridden = checklists.saved(sale, parts.id(), "terms", c -> {
			for (int i = 0; i < 2; i++) {
				set(c, "spaces." + i + ".chargeOverrides.water", checklists.sourced(checklists.charge("INCLUDED",
						null, null), "OBSERVED_ON_SITE"));
			}
		});
		assertFalse(unconfirmed(overridden).contains("TERMS/-/water"), unconfirmed(overridden).toString());
	}

	@Test
	void terms_partsBodyWithoutNewSpace_isAccepted() throws Exception {
		Draft draft = draft("PARTS", 1);
		JsonNode page2 = checklists.readOk(sale, draft.id()).body();
		checklists.saved(sale, draft.id(), "identification", c -> ((ArrayNode) c.path("spaces"))
				.add(checklists.space(checklists.newId(), "P102")));
		ObjectNode terms = ChecklistApiTestSupport.input(page2, "terms");
		set(terms, "spaces.0.monthlyRent", checklists.sourced(checklists.rent(5_000_000), "OBSERVED_ON_SITE"));

		Result saved = checklists.put(sale, draft.id(), "terms", ChecklistApiTestSupport.version(page2, "terms"),
				terms, null, null);

		assertEquals(200, saved.status(), saved.rawBody());
		assertEquals(2, saved.body().path("groups").path("terms").path("content").path("spaces").size());
	}

	// ==================== W16-12 ====================

	@Test
	void hazards_warnUntilSeenResolved_fixReportsNeedDateAndReceiver_unknownLinksAreRefused() throws Exception {
		Draft draft = draft("WHOLE", 1);
		String hazardId = checklists.newId();
		LocalDate today = LocalDate.now(SurveyTimeRules.ZONE);

		JsonNode open = checklists.saved(sale, draft.id(), "safety", c -> set(c, "hazards", checklists.value(
				List.of(checklists.hazard(hazardId, "UNCLASSIFIED", "OPEN", null)))));
		assertTrue(warnings(open).contains("OPEN_IMPORTANT_HAZARD/" + hazardId), warnings(open).toString());

		assertRefused(draft, "safety", c -> set(c, "hazards", checklists.value(List.of(checklists.hazard(hazardId,
				"IMPORTANT", "OWNER_REPORTED_FIXED", "Owner fixed it")))),
				"content.hazards.value.0.ownerReportedOn");
		JsonNode reported = checklists.saved(sale, draft.id(), "safety", c -> {
			ObjectNode hazard = checklists.hazard(hazardId, "IMPORTANT", "OWNER_REPORTED_FIXED", "Owner fixed it");
			hazard.put("ownerReportedOn", today.toString());
			hazard.put("receivedByUserId", sale.userId());
			set(c, "hazards", checklists.value(List.of(hazard)));
		});
		assertTrue(warnings(reported).contains("OPEN_IMPORTANT_HAZARD/" + hazardId));
		assertEquals("Save Sale", reported.path("groups").path("safety").path("content").path("hazards")
				.path("value").path(0).path("receivedByName").asString());

		JsonNode resolved = checklists.saved(sale, draft.id(), "safety", c -> set(c, "hazards", checklists.value(
				List.of(checklists.hazard(hazardId, "IMPORTANT", "OBSERVED_RESOLVED", "Railing replaced")))));
		assertFalse(warnings(resolved).contains("OPEN_IMPORTANT_HAZARD/" + hazardId));

		Draft other = draft("WHOLE", 1);
		String otherHazard = checklists.newId();
		checklists.saved(sale, other.id(), "safety", c -> set(c, "hazards", checklists.value(List.of(
				checklists.hazard(otherHazard, "IMPORTANT", "OPEN", null)))));
		Result unknownLink = linkTo(draft, checklists.newId(), checklists.newId());
		Result otherLead = linkTo(draft, other.id(), otherHazard);
		assertError(unknownLink, 400, "VALIDATION_FAILED", "content.hazards.value.0.previous");
		assertError(otherLead, 400, "VALIDATION_FAILED", "content.hazards.value.0.previous");
		assertEquals(unknownLink.body().path("errors").path(0).path("message").asString(),
				otherLead.body().path("errors").path(0).path("message").asString());
	}

	// ==================== W16-13 ====================

	@Test
	void staleGroupVersion_isAConflict_whileTwoPagesSavingDifferentGroupsBothSucceed() throws Exception {
		Draft draft = draft("WHOLE", 1);
		JsonNode read = checklists.readOk(sale, draft.id()).body();
		ObjectNode parts = ChecklistApiTestSupport.input(read, "parts");
		set(parts, "accessRoute", checklists.value("Through the alley"));
		ObjectNode dimensions = ChecklistApiTestSupport.input(read, "dimensions");
		set(dimensions, "spaces.0.bedrooms", checklists.sourced(2, "OBSERVED_ON_SITE"));

		Result first = checklists.put(sale, draft.id(), "parts", 0, parts, null, null);
		Result second = checklists.put(sale, draft.id(), "dimensions", 0, dimensions, null, null);
		set(parts, "accessRoute", checklists.value("Through the main gate"));
		Result stale = checklists.put(sale, draft.id(), "parts", 0, parts, null, null);

		assertEquals(200, first.status(), first.rawBody());
		assertEquals(200, second.status(), second.rawBody());
		assertError(stale, 409, "CONCURRENT_MODIFICATION", "expectedVersion");
		assertEquals("This checklist changed since you opened it.", stale.body().path("message").asString());
	}

	// ==================== W16-14 ====================

	@Test
	void readOnly_whileTheLeadIsHeld_andEditableAgainAfterTheResume() throws Exception {
		Draft draft = draft("WHOLE", 1);
		SurveyApiTestSupport.ok(survey.moveTo(leader, draft.visit().leadId(), "ON_HOLD"));

		assertError(checklists.save(sale, draft.id(), "parts", c -> set(c, "accessRoute",
				checklists.value("Alley"))), 409, "CHECKLIST_READ_ONLY", null);
		assertError(checklists.takeOver(sale, draft.id(), draft.visit()), 409, "CHECKLIST_READ_ONLY", null);
		JsonNode held = checklists.readOk(sale, draft.id()).body();
		assertFalse(held.path("editable").asBoolean());
		assertEquals("LEAD_STATUS", held.path("readOnlyReason").asString());

		resume(draft.visit().leadId());

		assertTrue(checklists.readOk(sale, draft.id()).body().path("editable").asBoolean());
		checklists.saved(sale, draft.id(), "parts", c -> set(c, "accessRoute", checklists.value("Alley")));
	}

	@Test
	void readOnly_afterARequirementChange_alsoAfterChangingBack() throws Exception {
		Draft draft = draft("WHOLE", 1);
		changeIntent(draft.visit().leadId(), "SHORT_TERM_RENT", null);
		changeIntent(draft.visit().leadId(), "LONG_TERM_RENT", AcquisitionApiTestSupport.price(12_000_000, "MONTH"));

		assertError(checklists.save(sale, draft.id(), "parts", c -> set(c, "accessRoute",
				checklists.value("Alley"))), 409, "CHECKLIST_READ_ONLY", null);
		assertEquals("INTENT_CHANGED", checklists.readOk(sale, draft.id()).body().path("readOnlyReason").asString());
	}

	@Test
	void readOnly_afterANewerVisit_butAScheduledResurveyDoesNotBlock() throws Exception {
		Draft draft = draft("WHOLE", 1);
		String resurvey = bookResurvey(draft.visit().leadId());

		checklists.saved(sale, draft.id(), "parts", c -> set(c, "accessRoute", checklists.value("Alley")));

		survey.shiftBack(resurvey, Duration.ofDays(2));
		Instant start = jdbc.queryForObject("select start_at from survey_appointments where id = ?",
				java.sql.Timestamp.class, resurvey).toInstant();
		Result visited = checklists.recordVisit(sale, draft.visit().leadId(), resurvey, start);
		assertEquals(200, visited.status(), visited.rawBody());

		assertError(checklists.save(sale, draft.id(), "parts", c -> set(c, "accessRoute",
				checklists.value("Main gate"))), 409, "CHECKLIST_READ_ONLY", null);
		assertEquals("NEWER_VISIT", checklists.readOk(sale, draft.id()).body().path("readOnlyReason").asString());
	}

	// ==================== W16-18 ====================

	@Test
	void readiness_newDraftListsEverything_aFilledDraftOnlyWaitsForPhotos() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());

		JsonNode fresh = checklists.readOk(sale, id).body();
		List<String> missing = missing(fresh);
		for (String item : List.of("IDENTIFICATION/-/rentalScope/SCOPE_MISSING", "IDENTIFICATION/-/spaces/NO_SPACES",
				"EVIDENCE/-/-/EVIDENCE_PENDING", "IDENTIFICATION/-/observedAddress/NOT_CHECKED",
				"PARTS/-/accessRoute/NOT_CHECKED", "SAFETY/-/hazards/NOT_CHECKED")) {
			assertTrue(missing.contains(item), item + " in " + missing);
		}
		assertFalse(fresh.path("readiness").path("submissionAvailable").asBoolean());

		Draft draft = new Draft(visit, id, List.of(checklists.newId()));
		checklists.saved(sale, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(draft.space(), "P101"))));
		JsonNode filled = fillNeeded(draft);

		assertEquals(List.of("EVIDENCE/-/-/EVIDENCE_PENDING", "IDENTIFICATION/-/differenceNote/DIFFERENCE_NOTE_MISSING"),
				missing(filled).stream().sorted().toList());
		assertTrue(unconfirmed(filled).contains("CONDITION/" + draft.space() + "/availableFrom"),
				unconfirmed(filled).toString());

		JsonNode noted = checklists.saved(sale, id, "identification", c -> c.put("differenceNote",
				"The lead gave the old house number"));
		assertEquals(List.of("EVIDENCE/-/-/EVIDENCE_PENDING"), missing(noted));
		JsonNode partial = checklists.saved(sale, id, "parts", c -> {
			ObjectNode access = checklists.object();
			access.put("extent", "ENTERED_PART");
			access.put("note", "The attic was locked");
			set(c, "spaces.0.actualAccess", checklists.value(access));
		});
		assertTrue(warnings(partial).contains("PARTIAL_ACCESS/-"), warnings(partial).toString());
		JsonNode outOfScope = checklists.saved(sale, id, "identification", c -> {
			ObjectNode fit = checklists.object();
			fit.put("assessment", "OUT_OF_SCOPE");
			fit.put("reason", "A shop house");
			set(c, "fit", checklists.value(fit));
			set(c, "propertyType", checklists.unknown(false));
		});
		assertTrue(missing(outOfScope).contains("IDENTIFICATION/-/fit/NOT_IN_SCOPE"), missing(outOfScope)
				.toString());
	}

	// ==================== W16-35 ====================

	@Test
	void ownerStatementsAndTheOwnerForm_areNeverShownAsSiteChecks() throws Exception {
		Draft draft = draft("WHOLE", 1);
		LocalDate stated = LocalDate.now(SurveyTimeRules.ZONE).minusDays(3);
		Map<String, Object> lead = jdbc.queryForMap("select address_detail, latitude, longitude, created_at from "
				+ "owner_leads where id = ?", draft.visit().leadId());
		String submittedOn = SurveyTimeRules.vietnamDate(((java.sql.Timestamp) lead.get("created_at")).toInstant())
				.toString();

		JsonNode conditions = checklists.saved(sale, draft.id(), "condition", c -> set(c, "spaces.0.availableFrom",
				checklists.ownerStated(LocalDate.now(SurveyTimeRules.ZONE).plusDays(10).toString(), stated,
						leader.userId(), "The leader heard it by phone")));
		JsonNode availableMeta = conditions.path("groups").path("condition").path("content").path("spaces").path(0)
				.path("availableFrom").path("meta");
		assertEquals(leader.userId(), availableMeta.path("observedByUserId").asString());
		assertEquals("Save Leader", availableMeta.path("observedByName").asString());
		assertEquals(stated.toString(), availableMeta.path("observedOn").asString());
		assertEquals("UNKNOWN", conditions.path("groups").path("condition").path("content").path("spaces")
				.path(0).path("occupancy").path("state").asString());

		JsonNode counts = checklists.saved(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.bedrooms",
				checklists.ownerStated(3, stated, leader.userId(), "The leader heard it by phone")));
		JsonNode bedroomsMeta = counts.path("groups").path("dimensions").path("content").path("spaces").path(0)
				.path("bedrooms").path("meta");
		assertEquals(leader.userId(), bedroomsMeta.path("observedByUserId").asString());
		assertEquals(stated.toString(), bedroomsMeta.path("observedOn").asString());

		JsonNode route = checklists.saved(sale, draft.id(), "parts", c -> set(c, "accessRoute",
				checklists.value("Alley next to the market")));
		JsonNode routeMeta = route.path("groups").path("parts").path("content").path("accessRoute").path("meta");
		assertEquals(sale.userId(), routeMeta.path("observedByUserId").asString());
		assertEquals(draft.visit().arrivalDate().toString(), routeMeta.path("observedOn").asString());

		JsonNode form = checklists.saved(sale, draft.id(), "identification", c -> set(c, "observedAddress",
				checklists.sourced(checklists.place((String) lead.get("address_detail"), SurveyApiTestSupport.WARD,
						(BigDecimal) lead.get("latitude"), (BigDecimal) lead.get("longitude")), "OWNER_FORM")));
		JsonNode addressMeta = form.path("groups").path("identification").path("content").path("observedAddress")
				.path("meta");
		assertEquals("address", addressMeta.path("formField").asString());
		assertEquals(submittedOn, addressMeta.path("observedOn").asString());
		assertTrue(addressMeta.path("observedByUserId").isNull());

		JsonNode rent = checklists.saved(sale, draft.id(), "terms", c -> set(c, "spaces.0.monthlyRent",
				checklists.sourced(checklists.rent(12_000_000), "OWNER_FORM")));
		JsonNode rentMeta = rent.path("groups").path("terms").path("content").path("spaces").path(0)
				.path("monthlyRent").path("meta");
		assertEquals("askingPrice", rentMeta.path("formField").asString());
		assertEquals(submittedOn, rentMeta.path("observedOn").asString());
	}

	@Test
	void ownerFormAvailableFrom_fourYearsAhead_equalToTheSubmission_isAccepted() throws Exception {
		Draft draft = draft("WHOLE", 1);
		LocalDate fourYears = LocalDate.now(SurveyTimeRules.ZONE).plusYears(4);
		jdbc.update("update owner_leads set available_from = ? where id = ?", java.sql.Date.valueOf(fourYears),
				draft.visit().leadId());

		JsonNode saved = checklists.saved(sale, draft.id(), "condition", c -> set(c, "spaces.0.availableFrom",
				checklists.sourced(fourYears.toString(), "OWNER_FORM")));

		assertEquals(fourYears.toString(), saved.path("groups").path("condition").path("content").path("spaces")
				.path(0).path("availableFrom").path("value").asString());
	}

	// ==================== W16-37 ====================

	@Test
	void scopeSwitch_partsToWholeClearsPartsOnlyUnits_andWholeToPartsClearsTheOwnerFormRent() throws Exception {
		Draft draft = draft("PARTS", 2);
		checklists.saved(sale, draft.id(), "condition", c -> {
			set(c, "sharedKitchen", checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
			set(c, "sharedBathroom", checklists.notApplicable("No shared bathroom", false));
		});
		checklists.saved(sale, draft.id(), "terms", c -> set(c, "spaces.0.chargeOverrides.electricity",
				checklists.sourced(checklists.charge("AMOUNT", 4_000L, "PER_KWH"), "OBSERVED_ON_SITE")));
		JsonNode read = checklists.readOk(sale, draft.id()).body();
		ObjectNode whole = ChecklistApiTestSupport.input(read, "identification");
		whole.put("rentalScope", "WHOLE");
		((ArrayNode) whole.path("spaces")).remove(1);
		Map<String, Object> missingCondition = ChecklistApiTestSupport.otherVersions(read);
		missingCondition.remove("condition");
		int savesBefore = allSaves(draft.id());
		int historyBefore = checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED").size();

		Result refused = checklists.put(sale, draft.id(), "identification",
				ChecklistApiTestSupport.version(read, "identification"), whole, null, missingCondition);
		assertError(refused, 409, "CONCURRENT_MODIFICATION", "otherGroupVersions.condition");

		Result switched = checklists.put(sale, draft.id(), "identification",
				ChecklistApiTestSupport.version(read, "identification"), whole, null,
				ChecklistApiTestSupport.otherVersions(read));

		assertEquals(200, switched.status(), switched.rawBody());
		JsonNode groups = switched.body().path("groups");
		assertTrue(groups.path("condition").path("content").path("sharedKitchen").isNull());
		assertTrue(groups.path("terms").path("content").path("spaces").path(0).path("chargeOverrides").isNull());
		assertEquals(savesBefore + 3, allSaves(draft.id()));
		assertEquals(historyBefore + 1, checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED").size());

		checklists.saved(sale, draft.id(), "terms", c -> set(c, "spaces.0.monthlyRent",
				checklists.sourced(checklists.rent(12_000_000), "OWNER_FORM")));
		JsonNode partsAgain = checklists.saved(sale, draft.id(), "identification", c -> {
			c.put("rentalScope", "PARTS");
			((ArrayNode) c.path("spaces")).add(checklists.space(checklists.newId(), "P109"));
		});
		JsonNode rent = partsAgain.path("groups").path("terms").path("content").path("spaces").path(0)
				.path("monthlyRent");
		assertEquals("UNKNOWN", rent.path("state").asString());
		assertEquals("OWNER_FORM", rent.path("meta").path("lastSource").asString());
		assertTrue(missing(partsAgain).contains("CONDITION/-/sharedKitchen/NOT_CHECKED"), missing(partsAgain)
				.toString());
	}

	// ==================== W16-38 ====================

	@Test
	void correctionBasisDates_outsideArrivalToToday_are400() throws Exception {
		Draft draft = draft("WHOLE", 1);
		checklists.saved(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("30"), "MEASURED")));
		checklists.reassign(leader, draft.visit().leadId(), saleB);
		assertEquals(201, checklists.takeOver(saleB, draft.id(), draft.visit()).status());
		Consumer<ObjectNode> edit = c -> set(c, "spaces.0.area", checklists.sourced(new BigDecimal("31"),
				"MEASURED"));

		assertError(checklists.save(saleB, draft.id(), "dimensions", ChecklistApiTestSupport.correction(
				"Measured again", draft.visit().arrivalDate().minusDays(1), "Owner's plan"), edit), 400,
				"VALIDATION_FAILED", "correction.basis.confirmedOn");
		assertError(checklists.save(saleB, draft.id(), "dimensions", ChecklistApiTestSupport.correction(
				"Measured again", LocalDate.now(SurveyTimeRules.ZONE).plusDays(1), "Owner's plan"), edit), 400,
				"VALIDATION_FAILED", "correction.basis.confirmedOn");
		Result corrected = checklists.save(saleB, draft.id(), "dimensions", ChecklistApiTestSupport.correction(
				"Measured again", draft.visit().arrivalDate(), "Owner's plan"), edit);
		assertEquals(200, corrected.status(), corrected.rawBody());
	}

	// ==================== W16-21 ====================

	@Test
	void saveRefusals_writeSecurityEvents_conflictsAndIneligibilityDoNot() throws Exception {
		Draft draft = draft("WHOLE", 1);
		Consumer<ObjectNode> edit = c -> set(c, "accessRoute", checklists.value("Alley"));
		JsonNode read = checklists.readOk(sale, draft.id()).body();
		ObjectNode parts = ChecklistApiTestSupport.input(read, "parts");
		edit.accept(parts);

		Result hidden = checklists.put(otherSale, draft.id(), "parts", 0, parts, null, null);
		Result notAssignee = checklists.put(leader, draft.id(), "parts", 0, parts, null, null);
		checklists.saved(sale, draft.id(), "parts", edit);
		set(parts, "accessRoute", checklists.value("Main gate"));
		Result stale = checklists.put(sale, draft.id(), "parts", 0, parts, null, null);

		assertError(hidden, 404, "NOT_FOUND", null);
		assertEquals(403, notAssignee.status(), notAssignee.rawBody());
		assertError(stale, 409, "CONCURRENT_MODIFICATION", "expectedVersion");
		List<Map<String, Object>> events = checklists.securityEvents(draft.visit().leadId());
		assertEquals(List.of("CHECKLIST:" + draft.id() + ":SAVE:NOT_VISIBLE:" + otherSale.userId(),
				"CHECKLIST:" + draft.id() + ":SAVE:NOT_ASSIGNEE:" + leader.userId()), events.stream()
				.map(event -> event.get("resource_type") + ":" + event.get("resource_id") + ":" + event.get("action")
						+ ":" + event.get("outcome") + ":" + event.get("user_id")).toList());
		assertEquals(hidden.header("X-Correlation-Id"), events.get(0).get("correlation_id"));

		TestUser leaving = survey.user("Save Leaving", "SALE");
		Visit ofLeaving = checklists.visited(leader, leaving);
		String leavingDraft = checklists.started(leaving, ofLeaving.appointmentId());
		checklists.makeIneligible(leaving);
		assertError(checklists.save(leaving, leavingDraft, "parts", edit), 422, "SURVEYOR_INELIGIBLE", null);
		assertEquals(List.of(), checklists.securityEvents(ofLeaving.leadId()));
	}

	// ==================== W16-40 ====================

	@Test
	void retryOfAHazardWithItsClientId_isANoOp() throws Exception {
		Draft draft = draft("WHOLE", 1);
		JsonNode read = checklists.readOk(sale, draft.id()).body();
		ObjectNode safety = ChecklistApiTestSupport.input(read, "safety");
		set(safety, "hazards", checklists.value(List.of(checklists.hazard(checklists.newId(), "MINOR", "OPEN",
				null))));

		Result first = checklists.put(sale, draft.id(), "safety", 0, safety, null, null);
		Result retry = checklists.put(sale, draft.id(), "safety", 0, safety, null, null);

		assertEquals(200, first.status(), first.rawBody());
		assertEquals(200, retry.status(), retry.rawBody());
		assertEquals(1, retry.body().path("groups").path("safety").path("content").path("hazards").path("value")
				.size());
		assertEquals(1, retry.body().path("groups").path("safety").path("version").asLong());
		assertEquals(1, saves(draft.id(), "SAFETY"));
		assertEquals(2, checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED").size());
	}

	@Test
	void unknownKeysServerFieldsAndForeignSources_are400OnTheirPath() throws Exception {
		Draft draft = draft("WHOLE", 1);
		JsonNode read = checklists.readOk(sale, draft.id()).body();

		ObjectNode unknownKey = ChecklistApiTestSupport.input(read, "parts");
		unknownKey.put("comment", "x");
		ObjectNode meta = ChecklistApiTestSupport.input(read, "parts");
		((ObjectNode) meta.path("accessRoute")).putNull("meta");
		ObjectNode serverField = ChecklistApiTestSupport.input(read, "identification");
		serverField.set("difference", checklists.object());
		ObjectNode foreignSource = ChecklistApiTestSupport.input(read, "parts");
		((ObjectNode) foreignSource.path("accessRoute")).put("source", "OWNER_STATED");
		Map<String, Object> partsWithOthers = new LinkedHashMap<>();
		partsWithOthers.put("expectedVersion", 0);
		partsWithOthers.put("otherGroupVersions", Map.of("dimensions", 0));
		partsWithOthers.put("correction", null);
		partsWithOthers.put("content", ChecklistApiTestSupport.input(read, "parts"));

		assertError(checklists.put(sale, draft.id(), "parts", 0, unknownKey, null, null), 400, "VALIDATION_FAILED",
				"content.comment");
		assertError(checklists.put(sale, draft.id(), "parts", 0, meta, null, null), 400, "VALIDATION_FAILED",
				"content.accessRoute.meta");
		assertError(checklists.put(sale, draft.id(), "identification", ChecklistApiTestSupport.version(read,
				"identification"), serverField, null, null), 400, "VALIDATION_FAILED", "content.difference");
		assertError(checklists.put(sale, draft.id(), "parts", 0, foreignSource, null, null), 400,
				"VALIDATION_FAILED", "content.accessRoute.source");
		assertError(api.put(sale, ChecklistApiTestSupport.CHECKLISTS + "/" + draft.id() + "/parts",
				partsWithOthers), 400, "VALIDATION_FAILED", "otherGroupVersions");
		assertEquals(0, saves(draft.id(), "PARTS"));
	}

	// ==================== W16-42 ====================

	@Test
	void aDraftOnALaterVisit_startsEmpty_andRecordsThatVisit() throws Exception {
		// the first visit three days back: the re-survey visit below (a day back) falls on a later Vietnam date at any
		// time of day
		String leadId = survey.lead(leader, sale, "CONTACTED", "LONG_TERM_RENT",
				AcquisitionApiTestSupport.price(12_000_000, "MONTH"));
		Draft first = draft(checklists.visit(sale, leadId, Duration.ofDays(3)), "WHOLE", 1);
		checklists.saved(sale, first.id(), "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("30"), "MEASURED")));
		SurveyApiTestSupport.ok(survey.moveTo(leader, first.visit().leadId(), "ON_HOLD"));
		SurveyApiTestSupport.ok(survey.moveTo(leader, first.visit().leadId(), "CONTACTED"));
		String resurvey = bookResurvey(first.visit().leadId());
		survey.shiftBack(resurvey, Duration.ofDays(2));
		Instant arrived = jdbc.queryForObject("select start_at from survey_appointments where id = ?",
				java.sql.Timestamp.class, resurvey).toInstant();
		assertEquals(200, checklists.recordVisit(sale, first.visit().leadId(), resurvey, arrived).status());

		String second = checklists.started(sale, resurvey);
		JsonNode fresh = checklists.readOk(sale, second).body();
		assertEquals(2, fresh.path("checklistNo").asInt());
		assertEquals("UNKNOWN", fresh.path("groups").path("identification").path("content")
				.path("observedAddress").path("state").asString());
		assertEquals(0, fresh.path("groups").path("identification").path("content").path("spaces").size());
		String spaceId = checklists.newId();
		checklists.saved(sale, second, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "P101"))));

		JsonNode saved = checklists.saved(sale, second, "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("31"), "MEASURED")));
		assertError(checklists.save(sale, second, "condition", c -> ((ObjectNode) c.path("spaces").path(0)
				.path("mainSpace")).put("source", "OWNER_STATED")), 400, "VALIDATION_FAILED",
				"content.spaces.0.mainSpace.source");

		JsonNode meta = saved.path("groups").path("dimensions").path("content").path("spaces").path(0).path("area")
				.path("meta");
		assertEquals(SurveyTimeRules.vietnamDate(arrived).toString(), meta.path("observedOn").asString());
		assertNotEquals(first.visit().arrivalDate().toString(), meta.path("observedOn").asString());
		JsonNode firstRead = checklists.readOk(sale, first.id()).body();
		assertEquals(0, new BigDecimal("30").compareTo(firstRead.path("groups").path("dimensions").path("content")
				.path("spaces").path(0).path("area").path("value").decimalValue()));
	}

	// ==================== Review Focus 1 ====================

	@Test
	void save_sameContentInNfdAndTrailingZero_isNoOp() throws Exception {
		Draft draft = draft("WHOLE", 1);
		String route = "Đường vào cạnh chợ";
		checklists.saved(sale, draft.id(), "parts", c -> set(c, "accessRoute", checklists.value(route)));
		checklists.saved(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("45.0"), "MEASURED")));
		Map<String, Long> versions = groupVersions(draft.id());

		JsonNode parts = checklists.saved(sale, draft.id(), "parts", c -> set(c, "accessRoute", checklists.value(
				Normalizer.normalize(route, Normalizer.Form.NFD) + " ")));
		JsonNode dimensions = checklists.saved(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.area",
				checklists.sourced(new BigDecimal("45"), "MEASURED")));

		assertEquals(versions.get("PARTS").longValue(), parts.path("groups").path("parts").path("version").asLong());
		assertEquals(versions.get("DIMENSIONS").longValue(), dimensions.path("groups").path("dimensions")
				.path("version").asLong());
		assertEquals(3, checklists.activities(draft.visit().leadId(), "CHECKLIST_SAVED").size());
	}

	@Test
	void identification_pinWithMoreDecimals_resentIsNoOp() throws Exception {
		Draft draft = draft("WHOLE", 1);
		ObjectNode pinned = checklists.place("12 Bạch Đằng", SurveyApiTestSupport.WARD, new BigDecimal("16.06780012"),
				new BigDecimal("108.22080049"));
		JsonNode saved = checklists.saved(sale, draft.id(), "identification", c -> set(c, "observedAddress.value",
				pinned));
		long version = ChecklistApiTestSupport.version(saved, "identification");

		JsonNode again = checklists.saved(sale, draft.id(), "identification", c -> set(c, "observedAddress.value",
				pinned));

		assertEquals(0, new BigDecimal("16.067800").compareTo(saved.path("groups").path("identification")
				.path("content").path("observedAddress").path("value").path("latitude").decimalValue()));
		assertEquals(version, ChecklistApiTestSupport.version(again, "identification"));
	}

	// ==================== helpers ====================

	/** A visited lead of the Sale and a draft whose G1 is saved with the scope and spaces (null scope: G1 empty). */
	private Draft draft(String scope, int spaceCount) throws Exception {
		return draft(checklists.visited(leader, sale), scope, spaceCount);
	}

	private Draft draft(Visit visit, String scope, int spaceCount) throws Exception {
		String id = checklists.started(sale, visit.appointmentId());
		List<String> spaces = new ArrayList<>();
		ObjectNode[] nodes = new ObjectNode[spaceCount];
		for (int i = 0; i < spaceCount; i++) {
			spaces.add(checklists.newId());
			nodes[i] = checklists.space(spaces.get(i), "P10" + (i + 1));
		}
		if (scope != null) {
			checklists.saved(sale, id, "identification", c -> c.setAll(checklists.identification(scope, nodes)));
		}
		return new Draft(visit, id, spaces);
	}

	/** Fills every needed field of G2..G5 for the one space (G1 is already saved). */
	private JsonNode fillNeeded(Draft draft) throws Exception {
		checklists.saved(sale, draft.id(), "parts", c -> {
			set(c, "accessRoute", checklists.value("Alley next to the market"));
			set(c, "sharedAreas", checklists.notApplicable("Nothing shared", false));
			set(c, "spaces.0.floor", checklists.value("Ground"));
			set(c, "spaces.0.privateAreas", checklists.value("Whole house"));
			ObjectNode access = checklists.object();
			access.put("extent", "ENTERED_ALL");
			access.putNull("note");
			set(c, "spaces.0.actualAccess", checklists.value(access));
		});
		checklists.saved(sale, draft.id(), "dimensions", c -> {
			set(c, "spaces.0.area", checklists.sourced(new BigDecimal("80"), "MEASURED"));
			set(c, "spaces.0.bedrooms", checklists.sourced(3, "OBSERVED_ON_SITE"));
			set(c, "spaces.0.privateBathrooms", checklists.sourced(2, "OBSERVED_ON_SITE"));
			set(c, "spaces.0.sharedBathroomUse", checklists.sourced("NO", "OBSERVED_ON_SITE"));
		});
		checklists.saved(sale, draft.id(), "condition", c -> {
			set(c, "spaces.0.occupancy", checklists.value("VACANT"));
			for (String area : List.of("mainSpace", "kitchen", "bathroom")) {
				set(c, "spaces.0." + area, checklists.value(checklists.areaCheck("INSPECTED", true, null, null)));
			}
		});
		return checklists.saved(sale, draft.id(), "safety", c -> {
			set(c, "egress", checklists.value("Front door and back door"));
			ObjectNode none = checklists.object();
			none.put("noneObserved", true);
			none.putNull("note");
			set(c, "accessRestrictions", checklists.value(none));
			set(c, "hazards", checklists.value(List.of()));
		});
	}

	private void assertRefused(Draft draft, String group, Consumer<ObjectNode> edit, String path) throws Exception {
		Result result = checklists.save(sale, draft.id(), group, edit);
		assertEquals(400, result.status(), path + ": " + result.rawBody());
		assertEquals("VALIDATION_FAILED", result.body().path("code").asString(), result.rawBody());
		assertTrue(fields(result).contains(path), path + " in " + result.rawBody());
	}

	private Result linkTo(Draft draft, String checklistId, String hazardId) throws Exception {
		return checklists.save(sale, draft.id(), "safety", c -> {
			ObjectNode hazard = checklists.hazard(checklists.newId(), "IMPORTANT", "OPEN", null);
			ObjectNode previous = checklists.object();
			previous.put("checklistId", checklistId);
			previous.put("hazardId", hazardId);
			hazard.set("previous", previous);
			set(c, "hazards", checklists.value(List.of(hazard)));
		});
	}

	private ObjectNode item(String code, String presence) {
		ObjectNode item = checklists.object();
		item.put("code", code);
		item.put("presence", presence);
		item.put("source", "OBSERVED_ON_SITE");
		item.putNull("confirmedOn");
		item.putNull("receivedByUserId");
		item.putNull("relayBasis");
		return item;
	}

	private ObjectNode deposit(String basis, Integer months, Long amount) {
		ObjectNode deposit = checklists.object();
		deposit.put("basis", basis);
		deposit.put("months", months);
		deposit.set("amount", amount == null ? null : checklists.money(amount));
		return deposit;
	}

	private void resume(String leadId) throws Exception {
		Map<String, Object> resume = new LinkedHashMap<>();
		resume.put("expectedLeadVersion", survey.version(leadId));
		resume.put("reason", "Owner is back");
		resume.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		Result resumed = api.post(sale, SurveyApiTestSupport.LEADS + "/" + leadId + "/survey-resumptions", resume);
		assertEquals(201, resumed.status(), resumed.rawBody());
	}

	private void changeIntent(String leadId, String intent, Map<String, Object> priceOrNull) throws Exception {
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("expectedVersion", survey.version(leadId));
		change.put("newIntent", intent);
		change.put("askingPrice", priceOrNull);
		change.put("reason", "Owner changed plans");
		change.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		SurveyApiTestSupport.ok(api.post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/intent-changes",
				change));
	}

	/** Holds the lead, moves it back to CONTACTED (when needed) and books a re-survey; returns its id. */
	private String bookResurvey(String leadId) throws Exception {
		if ("SURVEY_SCHEDULED".equals(survey.leadStatus(leadId))) {
			SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "ON_HOLD"));
			SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "CONTACTED"));
		}
		Map<String, Object> body = survey.booking(leadId, SurveyApiTestSupport.startIn(30));
		body.put("resurveyReason", "Owner rebuilt the kitchen");
		Result booked = survey.book(sale, leadId, body);
		assertEquals(201, booked.status(), booked.rawBody());
		return booked.body().path("survey").path("appointments").path(0).path("id").asString();
	}

	private Map<String, Long> groupVersions(String checklistId) {
		Map<String, Long> versions = new LinkedHashMap<>();
		jdbc.queryForList("select group_key, version from survey_checklist_groups where checklist_id = ? order by "
				+ "group_key", checklistId).forEach(row -> versions.put((String) row.get("group_key"),
				((Number) row.get("version")).longValue()));
		return versions;
	}

	private int saves(String checklistId, String group) {
		return checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ? and group_key = ?",
				checklistId, group);
	}

	private int allSaves(String checklistId) {
		return checklists.rows("select count(*) from survey_checklist_saves where checklist_id = ?", checklistId);
	}

	private static List<String> fields(Result result) {
		List<String> fields = new ArrayList<>();
		result.body().path("errors").forEach(error -> fields.add(error.path("field").asString()));
		return fields;
	}

	/** Readiness items as "GROUP/spaceId or -/field or -/PROBLEM". */
	private static List<String> missing(JsonNode checklist) {
		List<String> items = new ArrayList<>();
		checklist.path("readiness").path("missing").forEach(item -> items.add(item.path("group").asString() + "/"
				+ orDash(item.path("spaceId")) + "/" + orDash(item.path("field")) + "/"
				+ item.path("problem").asString()));
		return items;
	}

	private static List<String> unconfirmed(JsonNode checklist) {
		List<String> items = new ArrayList<>();
		checklist.path("readiness").path("unconfirmedTerms").forEach(item -> items.add(item.path("group").asString()
				+ "/" + orDash(item.path("spaceId")) + "/" + item.path("field").asString()));
		return items;
	}

	/** Warnings as "TYPE/hazardId or -". */
	private static List<String> warnings(JsonNode checklist) {
		List<String> items = new ArrayList<>();
		checklist.path("readiness").path("warnings").forEach(item -> items.add(item.path("type").asString() + "/"
				+ orDash(item.path("hazardId"))));
		return items;
	}

	private static String orDash(JsonNode node) {
		return node.isNull() || node.isMissingNode() ? "-" : node.asString();
	}

	private static String last(List<String> values) {
		return values.get(values.size() - 1);
	}
}
