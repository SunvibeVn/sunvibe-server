package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

/**
 * Functional limits without a time budget (survey-checklist spec A-6, risks 9.6 and 9.14; W16-23): a draft of 50
 * spaces with every text at its limit saves each group, takes over with its checkpoint and reads back whole; the fourth
 * draft of a four-visit chain with 30 hazards per checklist reads its earlier hazards. Needs Docker.
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
class ChecklistLimitsPostgresTest {

	private static final int SPACES = 50;
	private static final int HAZARDS = 30;
	private static final List<String> CODES = List.of("AIR_CONDITIONER", "WATER_HEATER", "WASHING_MACHINE",
			"REFRIGERATOR", "BED", "WARDROBE", "DESK", "KITCHEN_EQUIPMENT", "INTERNET", "ELEVATOR",
			"MOTORBIKE_PARKING", "CAR_PARKING", "BALCONY", "WINDOW", "SECURITY", "CCTV", "POOL", "GARDEN");

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
	private TestUser sale;
	private TestUser saleB;
	private TestUser leader;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0960");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		sale = survey.user("Limits Sale", "SALE");
		saleB = survey.user("Limits Sale B", "SALE");
		leader = survey.user("Limits Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	@Test
	void fiftySpacesWithEveryTextAtItsLimit_saveTakeOverAndReadBackWhole() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		List<ObjectNode> spaces = new ArrayList<>();
		for (int i = 0; i < SPACES; i++) {
			ObjectNode space = checklists.space(checklists.newId(), "Room " + letters(i));
			space.put("location", text(200));
			space.put("description", text(500));
			spaces.add(space);
		}

		JsonNode identification = checklists.saved(sale, id, "identification", c -> {
			c.setAll(checklists.identification("PARTS", spaces.toArray(ObjectNode[]::new)));
			set(c, "observedAddress.value.addressText", checklists.tree(text(255)));
			set(c, "fit.value.reason", checklists.tree(text(1000)));
			c.put("differenceNote", text(500));
			c.put("otherPartsNote", text(1000));
			c.put("generalNote", text(2000));
		});
		checklists.saved(sale, id, "parts", c -> {
			set(c, "accessRoute", checklists.value(text(1000)));
			set(c, "sharedAreas", checklists.value(text(1000)));
			for (int i = 0; i < SPACES; i++) {
				set(c, "spaces." + i + ".floor", checklists.value(text(50)));
				set(c, "spaces." + i + ".privateAreas", checklists.value(text(1000)));
				ObjectNode access = checklists.object();
				access.put("extent", "ENTERED_PART");
				access.put("note", text(500));
				set(c, "spaces." + i + ".actualAccess", checklists.value(access));
			}
		});
		checklists.saved(sale, id, "dimensions", c -> {
			for (int i = 0; i < SPACES; i++) {
				set(c, "spaces." + i + ".area", checklists.sourced(new BigDecimal("99999.9"), "MEASURED"));
				set(c, "spaces." + i + ".bedrooms", checklists.sourced(50, "OBSERVED_ON_SITE"));
				((ObjectNode) c.path("spaces").path(i)).put("layoutNote", text(1000));
			}
		});
		checklists.saved(sale, id, "condition", c -> {
			set(c, "sharedKitchen", checklists.value(checklists.areaCheck("INSPECTED", false, text(1000), null)));
			set(c, "sharedBathroom", checklists.value(checklists.areaCheck("NOT_ENTERED", null, text(1000),
					text(500))));
			for (int i = 0; i < SPACES; i++) {
				for (String area : List.of("mainSpace", "kitchen", "bathroom")) {
					set(c, "spaces." + i + "." + area, checklists.value(checklists.areaCheck("NOT_ENTERED", null,
							text(1000), text(500))));
				}
			}
		});
		checklists.saved(sale, id, "safety", c -> {
			set(c, "egress", checklists.value(text(1000)));
			ObjectNode restrictions = checklists.object();
			restrictions.put("noneObserved", false);
			restrictions.put("note", text(1000));
			set(c, "accessRestrictions", checklists.value(restrictions));
			List<ObjectNode> hazards = new ArrayList<>();
			for (int i = 0; i < HAZARDS; i++) {
				ObjectNode hazard = checklists.hazard(checklists.newId(), "IMPORTANT", "OBSERVED_RESOLVED", text(500));
				hazard.put("description", text(500));
				hazard.put("spaceId", spaces.get(i).path("spaceId").asString());
				hazards.add(hazard);
			}
			set(c, "hazards", checklists.value(hazards));
		});
		checklists.saved(sale, id, "amenities", c -> {
			ArrayNode shared = (ArrayNode) c.path("sharedItems");
			CODES.forEach(code -> shared.add(item(code)));
			for (int i = 0; i < SPACES; i++) {
				ArrayNode items = (ArrayNode) c.path("spaces").path(i).path("items");
				CODES.forEach(code -> items.add(item(code)));
			}
			ArrayNode others = (ArrayNode) c.path("otherItems");
			for (int i = 0; i < 20; i++) {
				ObjectNode other = item("BED");
				other.remove("code");
				other.put("itemId", checklists.newId());
				other.put("label", text(100));
				other.putNull("spaceId");
				others.add(other);
			}
		});
		JsonNode saved = checklists.saved(sale, id, "terms", c -> {
			for (String charge : List.of("electricity", "water", "internet", "parking", "serviceFee")) {
				set(c, "common." + charge, checklists.sourced(charge(), "OBSERVED_ON_SITE"));
			}
			set(c, "common.otherFees", checklists.sourced(fees(), "OBSERVED_ON_SITE"));
			set(c, "common.houseRules", checklists.sourced(text(2000), "OBSERVED_ON_SITE"));
			set(c, "common.handoverMaintenance", checklists.sourced(text(2000), "OBSERVED_ON_SITE"));
			ObjectNode pets = checklists.object();
			pets.put("policy", "CONDITIONAL");
			pets.put("note", text(500));
			set(c, "common.pets", checklists.sourced(pets, "OBSERVED_ON_SITE"));
			for (int i = 0; i < SPACES; i++) {
				for (String charge : List.of("electricity", "water", "internet", "parking", "serviceFee")) {
					set(c, "spaces." + i + ".chargeOverrides." + charge, checklists.sourced(charge(),
							"OBSERVED_ON_SITE"));
				}
				set(c, "spaces." + i + ".spaceFees", checklists.sourced(fees(), "OBSERVED_ON_SITE"));
			}
		});
		checklists.reassign(leader, visit.leadId(), saleB);
		Result takenOver = checklists.takeOver(saleB, id, visit);
		Result checkpoint = checklists.checkpoint(saleB, id, 1);
		JsonNode read = checklists.readOk(saleB, id).body();

		assertEquals(201, takenOver.status(), takenOver.rawBody());
		assertEquals(200, checkpoint.status());
		assertEquals(saved.path("groups"), checkpoint.body().path("snapshot").path("groups"));
		assertEquals(SPACES, read.path("groups").path("identification").path("content").path("spaces").size());
		assertEquals(text(2000), read.path("groups").path("identification").path("content").path("generalNote")
				.asString());
		assertEquals(HAZARDS, read.path("groups").path("safety").path("content").path("hazards").path("value").size());
		assertEquals(SPACES, read.path("effectiveTerms").size());
		assertEquals(text(1000), read.path("groups").path("condition").path("content").path("spaces").path(SPACES - 1)
				.path("bathroom").path("value").path("note").asString());
		assertEquals(identification.path("groups").path("identification"), read.path("groups")
				.path("identification"));
	}

	@Test
	void theFourthDraftOfAChain_with30HazardsPerChecklist_readsItsEarlierHazards() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		String last = null;
		for (int n = 0; n < 4; n++) {
			last = draftOnNextVisit(leadId, n);
			List<ObjectNode> hazards = new ArrayList<>();
			for (int i = 0; i < HAZARDS; i++) {
				ObjectNode hazard = checklists.hazard(checklists.newId(), "IMPORTANT", "OPEN", null);
				hazard.put("description", text(500));
				hazards.add(hazard);
			}
			String id = last;
			checklists.saved(sale, id, "safety", c -> set(c, "hazards", checklists.value(hazards)));
		}

		JsonNode fourth = checklists.readOk(sale, last).body();

		assertEquals(3 * HAZARDS, fourth.path("earlierHazards").size());
		long earlierOpen = 0;
		for (JsonNode warning : fourth.path("readiness").path("warnings")) {
			if ("EARLIER_HAZARD_OPEN".equals(warning.path("type").asString())) {
				earlierOpen++;
			}
		}
		assertEquals(3 * HAZARDS, earlierOpen);
	}

	/**
	 * Risk 9.11 (spec A-29): a tombstone per removed itemId or hazardId has no count limit, so G6 and G5 documents
	 * with 100 tombstones each still save, read back and go into a takeover checkpoint.
	 */
	@Test
	void hundredTombstonesInG6AndG5_saveAndReadBack() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		String id = checklists.started(sale, visit.appointmentId());
		String spaceId = checklists.newId();
		checklists.saved(sale, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "P101"))));
		for (int round = 0; round < 5; round++) {
			List<ObjectNode> items = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				items.add(otherItem(checklists.newId(), text(100)));
			}
			checklists.saved(sale, id, "amenities", c -> items.forEach(c.putArray("otherItems")::add));
			checklists.saved(sale, id, "amenities", c -> c.putArray("otherItems"));
		}
		for (int batch : new int[] {HAZARDS, HAZARDS, HAZARDS, 10}) {
			List<ObjectNode> hazards = new ArrayList<>();
			for (int i = 0; i < batch; i++) {
				hazards.add(checklists.hazard(checklists.newId(), "MINOR", "OPEN", null));
			}
			checklists.saved(sale, id, "safety", c -> set(c, "hazards", checklists.value(hazards)));
			checklists.saved(sale, id, "safety", c -> set(c, "hazards", checklists.value(List.of())));
		}

		JsonNode saved = checklists.saved(sale, id, "amenities", c -> c.putArray("otherItems").add(otherItem(
				checklists.newId(), "Curtains")));
		assertEquals(1, saved.path("groups").path("amenities").path("content").path("otherItems").size());
		assertEquals(100, storedTombstones(id, "AMENITIES").size());
		assertEquals(100, storedTombstones(id, "SAFETY").size());
		checklists.reassign(leader, visit.leadId(), saleB);
		Result takenOver = checklists.takeOver(saleB, id, visit);
		assertEquals(201, takenOver.status(), takenOver.rawBody());
		Result checkpoint = checklists.checkpoint(saleB, id, 1);
		assertEquals(200, checkpoint.status(), checkpoint.rawBody());
		assertEquals(0, checkpoint.body().path("snapshot").path("groups").path("safety").path("content")
				.path("hazards").path("value").size());
		assertEquals(200, checklists.read(saleB, id).status());
	}

	// ==================== helpers ====================

	private ObjectNode otherItem(String itemId, String label) {
		ObjectNode item = checklists.object();
		item.put("itemId", itemId);
		item.put("label", label);
		item.putNull("spaceId");
		item.put("presence", "YES");
		item.put("source", "OBSERVED_ON_SITE");
		item.putNull("confirmedOn");
		item.putNull("receivedByUserId");
		item.putNull("relayBasis");
		return item;
	}

	/** The tombstones of the stored group document (server-only, spec A-29), read from PostgreSQL. */
	private JsonNode storedTombstones(String checklistId, String groupKey) {
		return checklists.api().json().readTree(jdbc.queryForObject("select content from survey_checklist_groups "
				+ "where checklist_id = ? and group_key = ?", String.class, checklistId, groupKey)).path("tombstones");
	}

	/** The next visit of the lead (a re-survey after a hold when it has one), moved back and visited, with a draft. */
	private String draftOnNextVisit(String leadId, int n) throws Exception {
		if ("SURVEY_SCHEDULED".equals(survey.leadStatus(leadId))) {
			SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "ON_HOLD"));
			SurveyApiTestSupport.ok(survey.moveTo(leader, leadId, "CONTACTED"));
		}
		Map<String, Object> body = survey.booking(leadId, SurveyApiTestSupport.startIn(24 + 6 * n));
		if (n > 0) {
			body.put("resurveyReason", "Check the hazards again");
		}
		Result booked = survey.book(sale, leadId, body);
		assertEquals(201, booked.status(), booked.rawBody());
		String appointmentId = booked.body().path("survey").path("appointments").path(0).path("id").asString();
		survey.shiftBack(appointmentId, Duration.ofDays(2));
		Instant arrivedAt = jdbc.queryForObject("select start_at from survey_appointments where id = ?",
				Timestamp.class, appointmentId).toInstant();
		Result visited = checklists.recordVisit(sale, leadId, appointmentId, arrivedAt);
		assertEquals(200, visited.status(), visited.rawBody());
		String id = checklists.started(sale, appointmentId);
		String spaceId = checklists.newId();
		checklists.saved(sale, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "P101"))));
		return id;
	}

	/** A text of exactly {@code length} characters with Vietnamese letters, no digits and no trailing space. */
	private static String text(int length) {
		String words = "Tường sơn mới cửa sổ hướng nam ";
		StringBuilder text = new StringBuilder();
		while (text.length() < length) {
			text.append(words);
		}
		text.setLength(length);
		text.setCharAt(length - 1, 'x');
		return text.toString();
	}

	/** A code of letters only for space number {@code i}: A, B, ... Z, AA, AB, ... */
	private static String letters(int i) {
		return i < 26 ? String.valueOf((char) ('A' + i)) : "A" + (char) ('A' + i - 26);
	}

	private ObjectNode item(String code) {
		ObjectNode item = checklists.object();
		item.put("code", code);
		item.put("presence", "YES");
		item.put("source", "OBSERVED_ON_SITE");
		item.putNull("confirmedOn");
		item.putNull("receivedByUserId");
		item.putNull("relayBasis");
		return item;
	}

	private ObjectNode charge() {
		ObjectNode charge = checklists.charge("AMOUNT", 999_999_999_999_999L, "PER_MONTH");
		charge.put("note", text(200));
		return charge;
	}

	private List<ObjectNode> fees() {
		List<ObjectNode> fees = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			ObjectNode fee = checklists.object();
			fee.put("name", text(100));
			fee.set("amount", checklists.money(999_999_999_999_999L));
			fee.put("chargeUnit", "ONE_OFF");
			fees.add(fee);
		}
		return fees;
	}
}
