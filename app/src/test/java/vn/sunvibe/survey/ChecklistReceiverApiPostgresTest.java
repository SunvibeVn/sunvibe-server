package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.survey.ChecklistApiTestSupport.set;
import static vn.sunvibe.survey.SurveyApiTestSupport.assertError;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
 * Who may be named as the receiver of an owner statement and what the owner's own web form lets a draft use
 * (survey-checklist BR-21, BR-25, spec A-18, A-25, A-27; W16-41). The SQL setups do what no product command does: a
 * removed History row and a lead that came from staff entry. Needs Docker.
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
class ChecklistReceiverApiPostgresTest {

	private static final List<String> OWNER_FORM_FIELDS = List.of("submittedOn", "addressDetail", "provinceCode",
			"wardCode", "latitude", "longitude", "monthlyAskingPrice", "availableFrom");

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
	private TestUser stranger;
	private TestUser leader;
	private TestUser manager;
	private LocalDate stated;

	/** A visited lead and the Sale's draft with G1 saved (WHOLE, one space). */
	private record Draft(Visit visit, String id, String spaceId) {
	}

	@BeforeAll
	void setUp() throws Exception {
		api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder, ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0966");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		sale = survey.user("Receiver Sale", "SALE");
		saleB = survey.user("Receiver Sale B", "SALE");
		stranger = survey.user("Receiver Stranger", "SALE");
		leader = survey.user("Receiver Leader", "SALE_LEADER");
		manager = survey.user("Receiver Manager", "SALES_MANAGER");
		stated = LocalDate.now(SurveyTimeRules.ZONE).minusDays(1);
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	// ==================== receivers (A-25) ====================

	@Test
	void aManagerWhoAddedANote_isAReceiver_withARelayBasisOnlyWhenSomeoneElseHeardIt() throws Exception {
		Draft draft = draft(sale, "WHOLE");
		note(manager, draft.visit().leadId());

		assertRefused(draft, bedrooms(manager, null), "content.spaces.0.bedrooms.relayBasis");
		assertRefused(draft, bedrooms(sale, "I heard it myself"), "content.spaces.0.bedrooms.relayBasis");
		JsonNode saved = checklists.saved(sale, draft.id(), "dimensions", bedrooms(manager,
				"The manager wrote it on the lead"));

		JsonNode meta = saved.path("groups").path("dimensions").path("content").path("spaces").path(0)
				.path("bedrooms").path("meta");
		assertEquals(manager.userId(), meta.path("observedByUserId").asString());
		assertEquals("Receiver Manager", meta.path("observedByName").asString());
		assertEquals(stated.toString(), meta.path("observedOn").asString());
	}

	@Test
	void staffWithoutATraceOnTheLead_andAnUnknownId_areRefusedAlike() throws Exception {
		Draft draft = draft(sale, "WHOLE");

		Result noTrace = checklists.save(sale, draft.id(), "dimensions", bedrooms(stranger, "Heard it"));
		Result unknown = checklists.save(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.bedrooms",
				checklists.ownerStated(3, stated, checklists.newId(), "Heard it")));

		assertError(noTrace, 400, "VALIDATION_FAILED", "content.spaces.0.bedrooms.receivedByUserId");
		assertError(unknown, 400, "VALIDATION_FAILED", "content.spaces.0.bedrooms.receivedByUserId");
		assertEquals(noTrace.body().path("errors").path(0).path("message").asString(),
				unknown.body().path("errors").path(0).path("message").asString());
	}

	@Test
	void aFormerSaleWithoutARole_namedOnTheHistory_isAcceptedAndNamed() throws Exception {
		TestUser former = survey.user("Receiver Former Sale", "SALE");
		Visit visit = checklists.visited(leader, former);
		String id = checklists.started(former, visit.appointmentId());
		String spaceId = checklists.newId();
		checklists.saved(former, id, "identification", c -> c.setAll(checklists.identification("WHOLE",
				checklists.space(spaceId, "P101"))));
		checklists.reassign(leader, visit.leadId(), sale);
		assertEquals(201, checklists.takeOver(sale, id, visit).status());
		jdbc.update("delete from user_roles where user_id = ?", former.userId());

		JsonNode saved = checklists.saved(sale, id, "dimensions", bedrooms(former, "Former Sale's call notes"));

		assertEquals("Receiver Former Sale", saved.path("groups").path("dimensions").path("content").path("spaces")
				.path(0).path("bedrooms").path("meta").path("observedByName").asString());
	}

	@Test
	void anUnchangedUnitKeepsItsReceiver_whenTheSetNoLongerHoldsThem() throws Exception {
		Draft draft = draft(sale, "WHOLE");
		note(manager, draft.visit().leadId());
		checklists.saved(sale, draft.id(), "dimensions", bedrooms(manager, "The manager wrote it on the lead"));
		jdbc.update("delete from owner_lead_activities where lead_id = ? and actor_user_id = ?",
				draft.visit().leadId(), manager.userId());

		JsonNode saved = checklists.saved(sale, draft.id(), "dimensions", c -> set(c, "spaces.0.privateBathrooms",
				checklists.sourced(1, "OBSERVED_ON_SITE")));

		assertEquals(manager.userId(), saved.path("groups").path("dimensions").path("content").path("spaces")
				.path(0).path("bedrooms").path("receivedByUserId").asString());
		assertRefused(draft, c -> set(c, "spaces.0.bedrooms", checklists.ownerStated(4, stated, manager.userId(),
				"The manager wrote it on the lead")), "content.spaces.0.bedrooms.receivedByUserId");
	}

	@Test
	void beingNamedAsReceiver_givesNoAccess() throws Exception {
		Draft draft = draft(sale, "WHOLE");
		checklists.reassign(leader, draft.visit().leadId(), saleB);
		checklists.reassign(leader, draft.visit().leadId(), sale);

		checklists.saved(sale, draft.id(), "dimensions", bedrooms(saleB, "Sale B took the call"));

		assertError(checklists.read(saleB, draft.id()), 404, "NOT_FOUND", null);
	}

	@Test
	void leadStaff_isTheReceiverSetWithNames_forAnEditorOnly() throws Exception {
		Draft draft = draft(sale, "WHOLE");
		note(manager, draft.visit().leadId());

		JsonNode asEditor = checklists.readOk(sale, draft.id()).body();
		JsonNode asLeader = checklists.readOk(leader, draft.id()).body();

		Set<String> ids = new TreeSet<>();
		asEditor.path("leadStaff").forEach(member -> {
			ids.add(member.path("userId").asString());
			assertTrue(member.path("name").asString().startsWith("Receiver "), member.toString());
		});
		assertEquals(new TreeSet<>(List.of(sale.userId(), leader.userId(), manager.userId())), ids);
		assertEquals(0, asLeader.path("leadStaff").size());
	}

	// ==================== the owner's form (A-18, A-27) ====================

	@Test
	void ownerForm_holdsOnlyTheSubmittedValues_andAnEditedAddressOrAnotherDateIsRefused() throws Exception {
		Draft draft = draft(sale, "WHOLE");
		JsonNode read = checklists.readOk(sale, draft.id()).body();
		JsonNode form = read.path("ownerForm");
		assertEquals(OWNER_FORM_FIELDS, AcquisitionApiTestSupport.fieldNames(form));
		assertEquals(12_000_000, form.path("monthlyAskingPrice").path("amount").asLong());
		assertEquals("2026-11-01", form.path("availableFrom").asString());

		assertRefused(draft, "identification", c -> set(c, "observedAddress", checklists.sourced(checklists.place(
				form.path("addressDetail").asString() + " (back gate)", SurveyApiTestSupport.WARD,
				form.path("latitude").decimalValue(), form.path("longitude").decimalValue()), "OWNER_FORM")),
				"content.observedAddress.source");
		assertRefused(draft, "condition", c -> set(c, "spaces.0.availableFrom", checklists.sourced("2026-11-02",
				"OWNER_FORM")), "content.spaces.0.availableFrom.source");
		checklists.saved(sale, draft.id(), "condition", c -> set(c, "spaces.0.availableFrom",
				checklists.sourced("2026-11-01", "OWNER_FORM")));
	}

	@Test
	void ownerFormRentOrDate_inSeparateParts_isRefused() throws Exception {
		Draft draft = draft(sale, "PARTS");

		assertRefused(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.sourced(
				checklists.rent(12_000_000), "OWNER_FORM")), "content.spaces.0.monthlyRent.source");
		assertRefused(draft, "condition", c -> set(c, "spaces.0.availableFrom", checklists.sourced("2026-11-01",
				"OWNER_FORM")), "content.spaces.0.availableFrom.source");
	}

	@Test
	void aNightAskingPrice_isNoMonthlyRentOfTheForm() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED", "SHORT_TERM_RENT",
				AcquisitionApiTestSupport.price(500_000, "NIGHT"));
		changeIntent(leadId, "LONG_TERM_RENT", AcquisitionApiTestSupport.price(9_000_000, "MONTH"));
		Draft draft = draftOn(sale, checklists.visit(sale, leadId), "WHOLE");

		assertTrue(checklists.readOk(sale, draft.id()).body().path("ownerForm").path("monthlyAskingPrice").isNull());
		assertRefused(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.sourced(
				checklists.rent(500_000), "OWNER_FORM")), "content.spaces.0.monthlyRent.source");
		assertRefused(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.sourced(
				checklists.rent(9_000_000), "OWNER_FORM")), "content.spaces.0.monthlyRent.source");
	}

	@Test
	void aPriceSetByALaterRequirementChange_isNotTheOwnersForm() throws Exception {
		String leadId = survey.lead(leader, sale, "CONTACTED");
		changeIntent(leadId, "SHORT_TERM_RENT", null);
		changeIntent(leadId, "LONG_TERM_RENT", AcquisitionApiTestSupport.price(13_000_000, "MONTH"));
		Visit visit = checklists.visit(sale, leadId);
		Draft draft = draftOn(sale, visit, "WHOLE");

		assertRefused(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.sourced(
				checklists.rent(13_000_000), "OWNER_FORM")), "content.spaces.0.monthlyRent.source");
		checklists.saved(sale, draft.id(), "terms", c -> set(c, "spaces.0.monthlyRent", checklists.sourced(
				checklists.rent(12_000_000), "OWNER_FORM")));
	}

	@Test
	void aStaffEntryLead_hasNoOwnerForm_andRefusesEveryOwnerFormSource() throws Exception {
		Draft draft = draft(sale, "WHOLE");
		jdbc.update("update owner_leads set source = 'STAFF_ENTRY' where id = ?", draft.visit().leadId());

		assertTrue(checklists.readOk(sale, draft.id()).body().path("ownerForm").isNull());
		assertRefused(draft, "terms", c -> set(c, "spaces.0.monthlyRent", checklists.sourced(
				checklists.rent(12_000_000), "OWNER_FORM")), "content.spaces.0.monthlyRent.source");
	}

	// ==================== helpers ====================

	private Draft draft(TestUser assignee, String scope) throws Exception {
		return draftOn(assignee, checklists.visited(leader, assignee), scope);
	}

	private Draft draftOn(TestUser assignee, Visit visit, String scope) throws Exception {
		String id = checklists.started(assignee, visit.appointmentId());
		String spaceId = checklists.newId();
		ObjectNode content = "WHOLE".equals(scope)
				? checklists.identification(scope, checklists.space(spaceId, "P101"))
				: checklists.identification(scope, checklists.space(spaceId, "P101"),
						checklists.space(checklists.newId(), "P102"));
		checklists.saved(assignee, id, "identification", c -> c.setAll(content));
		return new Draft(visit, id, spaceId);
	}

	private Consumer<ObjectNode> bedrooms(TestUser receiver, String relayBasisOrNull) {
		return c -> set(c, "spaces.0.bedrooms", checklists.ownerStated(3, stated, receiver.userId(),
				relayBasisOrNull));
	}

	private void note(TestUser author, String leadId) throws Exception {
		Map<String, Object> note = new LinkedHashMap<>();
		note.put("note", "Owner prefers calls after 6pm");
		Result added = api.post(author, SurveyApiTestSupport.LEADS + "/" + leadId + "/activities", note);
		assertTrue(added.status() == 200 || added.status() == 201, added.rawBody());
	}

	private void changeIntent(String leadId, String intent, Map<String, Object> priceOrNull) throws Exception {
		Map<String, Object> change = new LinkedHashMap<>();
		change.put("expectedVersion", survey.version(leadId));
		change.put("newIntent", intent);
		change.put("askingPrice", priceOrNull);
		change.put("reason", "Owner changed the price");
		change.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		SurveyApiTestSupport.ok(api.post(leader, SurveyApiTestSupport.LEADS + "/" + leadId + "/intent-changes",
				change));
	}

	private void assertRefused(Draft draft, Consumer<ObjectNode> edit, String path) throws Exception {
		assertRefused(draft, "dimensions", edit, path);
	}

	private void assertRefused(Draft draft, String group, Consumer<ObjectNode> edit, String path) throws Exception {
		Result result = checklists.save(sale, draft.id(), group, edit);
		assertError(result, 400, "VALIDATION_FAILED", path);
	}
}
