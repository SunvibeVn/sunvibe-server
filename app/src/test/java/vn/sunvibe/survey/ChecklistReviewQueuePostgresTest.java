package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistReviewApiSupport.Ready;
import vn.sunvibe.survey.core.domain.policy.common.SurveyTimeRules;

/**
 * GET /survey-checklists/review-queue (survey-checklist submission spec 7.1, S-18, plan PD-4): SUBMITTED revisions of
 * visible leads in a live cycle, oldest first, with whether the viewer can decide each one from batched reads. Needs
 * Docker.
 */
class ChecklistReviewQueuePostgresTest extends MediaApiTestBase {

	private static final String QUEUE = ChecklistReviewApiSupport.CHECKLISTS + "/review-queue";

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

	@Autowired
	private MediaTestStore store;

	@Autowired
	private MediaTestSwitches switches;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private ChecklistReviewApiSupport review;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser manager;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0964");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		review = new ChecklistReviewApiSupport(checklists, media, store, jdbc);
		sale = survey.user("Queue Sale", "SALE");
		otherSale = survey.user("Queue Other Sale", "SALE");
		leader = survey.user("Queue Leader", "SALE_LEADER");
		manager = survey.user("Queue Manager", "SALES_MANAGER");
	}

	@BeforeEach
	void open() {
		MediaIntakeFixture.open(jdbc);
		switches.submission(true);
	}

	@AfterEach
	void reset() {
		store.reset();
		switches.reset();
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	private Ready submitted(TestUser assignee) throws Exception {
		Ready ready = review.ready(leader, assignee);
		review.submitted(assignee, ready.checklistId());
		return ready;
	}

	private JsonNode queue(TestUser viewer) throws Exception {
		return queue(viewer, 0, 50);
	}

	private JsonNode queue(TestUser viewer, int page, int size) throws Exception {
		Result answer = checklists.api().get(viewer, QUEUE + "?page=" + page + "&size=" + size);
		SurveyApiTestSupport.ok(answer);
		return answer.body();
	}

	private static List<String> ids(JsonNode page) {
		List<String> ids = new ArrayList<>();
		page.path("items").forEach(item -> ids.add(item.path("checklistId").asString()));
		return ids;
	}

	private static JsonNode row(JsonNode page, String checklistId) {
		for (JsonNode item : page.path("items")) {
			if (checklistId.equals(item.path("checklistId").asString())) {
				return item;
			}
		}
		throw new AssertionError("No row for " + checklistId + " in " + page);
	}

	private void hold(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "ON_HOLD");
		body.put("expectedVersion", survey.version(leadId));
		body.put("note", "Owner abroad");
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId
				+ "/status-changes", body));
	}

	private void changeIntent(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("expectedVersion", survey.version(leadId));
		body.put("newIntent", "SHORT_TERM_RENT");
		body.put("askingPrice", null);
		body.put("reason", "Owner changed plans");
		body.put("basis", ChecklistApiTestSupport.basis(LocalDate.now(SurveyTimeRules.ZONE), "Owner called"));
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId
				+ "/intent-changes", body));
	}

	private void close(String leadId) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("toStatus", "WITHDRAWN");
		body.put("expectedVersion", survey.version(leadId));
		body.put("reason", "Owner sold the house");
		SurveyApiTestSupport.ok(checklists.api().post(leader, SurveyApiTestSupport.LEADS + "/" + leadId
				+ "/status-changes", body));
	}

	@Test
	void queue_oldestFirst() throws Exception {
		Ready first = submitted(sale);
		Ready second = submitted(sale);

		List<String> ids = ids(queue(manager));

		assertTrue(ids.indexOf(first.checklistId()) < ids.indexOf(second.checklistId()), ids.toString());
	}

	@Test
	void queue_ignoresDeadRevisions_totalsCorrect() throws Exception {
		long before = queue(manager).path("totalElements").asLong();
		Ready live = submitted(sale);
		Ready heldLead = submitted(sale);
		Ready endedCycle = submitted(sale);
		Ready closed = submitted(sale);
		Ready decided = submitted(sale);
		hold(heldLead.leadId());
		changeIntent(endedCycle.leadId());
		close(closed.leadId());
		review.passed(leader, decided.checklistId());

		JsonNode page = queue(manager);

		assertEquals(before + 2, page.path("totalElements").asLong());
		List<String> ids = ids(page);
		assertTrue(ids.contains(live.checklistId()));
		assertTrue(ids.contains(heldLead.checklistId()), "a held lead stays in the queue, with its reason");
		assertFalse(ids.contains(endedCycle.checklistId()));
		assertFalse(ids.contains(closed.checklistId()));
		assertFalse(ids.contains(decided.checklistId()));
		assertEquals("LEAD_STATUS", row(page, heldLead.checklistId()).path("reason").asString());
	}

	@Test
	void queue_saleSeesOnlyOwnLeads_total() throws Exception {
		TestUser own = survey.user("Queue Own Sale", "SALE");
		Ready mine = submitted(own);
		submitted(otherSale);

		JsonNode page = queue(own);

		assertEquals(1, page.path("totalElements").asLong());
		assertEquals(List.of(mine.checklistId()), ids(page));
		assertFalse(row(page, mine.checklistId()).path("canReview").asBoolean());
		assertEquals("NOT_REVIEWER_ROLE", row(page, mine.checklistId()).path("reason").asString());
	}

	@Test
	void queue_paged_totalIsTheWholeSet() throws Exception {
		TestUser own = survey.user("Queue Paged Sale", "SALE");
		Ready first = submitted(own);
		Ready second = submitted(own);
		Ready third = submitted(own);

		JsonNode firstPage = queue(own, 0, 2);
		JsonNode secondPage = queue(own, 1, 2);

		assertEquals(List.of(first.checklistId(), second.checklistId()), ids(firstPage));
		assertEquals(List.of(third.checklistId()), ids(secondPage));
		assertEquals(3, firstPage.path("totalElements").asLong());
		assertEquals(2, firstPage.path("size").asInt());
		assertEquals(1, secondPage.path("page").asInt());
	}

	@Test
	void queue_shape() throws Exception {
		Ready ready = submitted(sale);

		JsonNode item = row(queue(manager), ready.checklistId());

		assertEquals(ready.leadId(), item.path("leadId").asString());
		assertEquals(jdbc.queryForObject("select request_code from owner_leads where id = ?", String.class,
				ready.leadId()), item.path("leadCode").asString());
		assertEquals(jdbc.queryForObject("select checklist_no from survey_checklists where id = ?", Integer.class,
				ready.checklistId()), item.path("checklistNo").asInt());
		assertEquals(sale.userId(), item.path("submittedBy").path("userId").asString());
		assertEquals("Queue Sale", item.path("submittedBy").path("name").asString());
		assertTrue(item.hasNonNull("submittedAt"));
		assertTrue(item.path("canReview").asBoolean());
		assertTrue(item.path("reason").isNull());
	}

	@Test
	void queue_reasonForAssigneeLeader() throws Exception {
		Ready ready = submitted(sale);
		checklists.reassign(manager, ready.leadId(), leader);

		JsonNode item = row(queue(leader), ready.checklistId());

		assertFalse(item.path("canReview").asBoolean());
		assertEquals("NOT_INDEPENDENT", item.path("reason").asString());
		assertTrue(row(queue(manager), ready.checklistId()).path("canReview").asBoolean());
	}

	@Test
	void queue_servingOff_reasonServingDisabled() throws Exception {
		Ready ready = submitted(sale);
		switches.serving(false);

		assertEquals("SERVING_DISABLED", row(queue(manager), ready.checklistId()).path("reason").asString());
	}

	@Test
	void queue_sizeAbove50_400() throws Exception {
		Result refused = checklists.api().get(manager, QUEUE + "?size=51");

		assertEquals(400, refused.status(), refused.rawBody());
		assertEquals("VALIDATION_FAILED", refused.body().path("code").asString());
		assertEquals(20, queue(manager, 0, 20).path("size").asInt());
		SurveyApiTestSupport.ok(checklists.api().get(manager, QUEUE));
	}

	@Test
	void queue_noToken_401() throws Exception {
		assertEquals(401, checklists.api().get(null, QUEUE).status());
	}

	/** S-18: the number of statements does not grow with the rows of the page. */
	@Test
	void queue_batchedExclusion_fixedQueryCount() throws Exception {
		TestUser reviewerOfOne = survey.user("Queue Counted Leader", "SALE_LEADER");
		submitted(sale);
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		boolean enabled = statistics.isStatisticsEnabled();
		statistics.setStatisticsEnabled(true);
		try {
			queue(reviewerOfOne, 0, 1);
			statistics.clear();
			queue(reviewerOfOne, 0, 1);
			long forOne = statistics.getPrepareStatementCount();

			submitted(sale);
			submitted(sale);
			submitted(sale);
			statistics.clear();
			JsonNode page = queue(reviewerOfOne, 0, 4);

			assertEquals(4, page.path("items").size());
			assertEquals(forOne, statistics.getPrepareStatementCount());
		} finally {
			statistics.setStatisticsEnabled(enabled);
		}
	}
}
