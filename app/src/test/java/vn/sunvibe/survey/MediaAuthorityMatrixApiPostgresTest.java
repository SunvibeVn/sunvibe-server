package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.JsonNode;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.MediaApiTestSupport.Row;

/**
 * Who may not touch survey photos (survey-media spec 4.3, 4.9, A-19, A-23; SM-6; plan Task A24): another Sale gets the
 * not-found answer of a missing photo with a throttled event, a manager or an ADMIN who is not the holder gets 403 on a
 * removal, the roles outside sale staff and ADMIN get 403 on every media path, and a manager's hold or release is a
 * role 403 before any lookup with no event. Needs Docker.
 */
class MediaAuthorityMatrixApiPostgresTest extends MediaApiTestBase {

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
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;
	private TestUser manager;
	private TestUser admin;
	private List<TestUser> outsiders;

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0996");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Matrix Sale", "SALE");
		otherSale = survey.user("Matrix Other Sale", "SALE");
		leader = survey.user("Matrix Leader", "SALE_LEADER");
		manager = survey.user("Matrix Manager", "SALES_MANAGER");
		admin = survey.user("Matrix Admin", "ADMIN");
		outsiders = List.of(survey.user("Matrix Reviewer", "LISTING_REVIEWER"), survey.user("Matrix Owner", "OWNER"),
				survey.user("Matrix Customer", "CUSTOMER"));
	}

	@BeforeEach
	void openIntake() {
		MediaIntakeFixture.open(jdbc);
	}

	@AfterAll
	void tearDown() {
		MediaIntakeFixture.reset(jdbc);
		survey.cleanUp();
	}

	@Test
	void otherSale_404WithEvent_onListContentRemoval_oneBodyForMissingAndHidden() throws Exception {
		Draft draft = draft();
		String id = photo(draft);
		String missing = MediaApiTestSupport.newKey();
		int events = media.mediaEvents(draft.visit().leadId()).size();

		Result list = media.list(otherSale, draft.id(), null);
		Result content = media.put(otherSale, id, MediaApiTestSupport.bytes(1000, 1));
		Result removal = media.remove(otherSale, id, null);
		Result missingRemoval = media.remove(otherSale, missing, null);

		for (Result hidden : List.of(list, content, removal)) {
			assertEquals(404, hidden.status(), hidden.rawBody());
		}
		assertEquals(404, missingRemoval.status());
		assertEquals(body(missingRemoval), body(removal), "a hidden photo answers like a missing one");
		assertTrue(media.mediaEvents(draft.visit().leadId()).size() > events, "a NOT_VISIBLE event");
	}

	@Test
	void managerAndAdminNotHolder_403OnRemoval_managerWithEvent() throws Exception {
		Draft draft = draft();
		String id = photo(draft);
		int events = media.mediaEvents(draft.visit().leadId()).size();

		assertEquals(403, media.remove(manager, id, null).status());
		assertEquals(403, media.remove(admin, id, null).status());

		assertTrue(media.mediaEvents(draft.visit().leadId()).size() > events, "a NOT_ASSIGNEE event");
		assertEquals(null, media.row(id).get("removed_at"));
	}

	@Test
	void listingReviewerOwnerCustomer_403OnEveryMediaPath() throws Exception {
		Draft draft = draft();
		String id = photo(draft);
		String base = MediaApiTestSupport.MEDIA + "/" + id;
		for (TestUser outsider : outsiders) {
			List<Result> answers = List.of(media.list(outsider, draft.id(), null),
					media.create(outsider, draft.id(), MediaApiTestSupport.exterior(MediaApiTestSupport.sha("x"))),
					media.put(outsider, id, MediaApiTestSupport.bytes(1000, 2)),
					media.read(outsider, id, "thumbnail"),
					media.remove(outsider, id, null),
					checklists.api().post(outsider, base + "/hold", Map.of("reason", "SECURITY_INCIDENT")),
					checklists.api().post(outsider, base + "/hold-release", Map.of()));
			for (Result answer : answers) {
				assertEquals(403, answer.status(), outsider.userId() + " " + answer.rawBody());
			}
		}
	}

	@Test
	void managerHoldAndRelease_403NoEvent_beforeLookup() throws Exception {
		Draft draft = draft();
		String id = photo(draft);
		int events = jdbc.queryForObject("select count(*) from survey_security_events", Integer.class);

		for (String target : List.of(id, MediaApiTestSupport.newKey())) {
			assertEquals(403, checklists.api().post(manager, MediaApiTestSupport.MEDIA + "/" + target + "/hold",
					Map.of("reason", "SECURITY_INCIDENT")).status());
			assertEquals(403, checklists.api().post(manager, MediaApiTestSupport.MEDIA + "/" + target
					+ "/hold-release", Map.of()).status());
		}

		assertEquals(events, jdbc.queryForObject("select count(*) from survey_security_events", Integer.class));
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private String photo(Draft draft) {
		return media.insertPhoto(draft.id(), sale.userId(), Row.READY, Instant.now().minus(Duration.ofMinutes(5)),
				MediaApiTestSupport.sha(MediaApiTestSupport.newKey()));
	}

	/** The error body without its correlation id. */
	private static String body(Result result) {
		JsonNode body = result.body();
		return body.path("code").asString() + "|" + body.path("message").asString() + "|" + body.path("errors");
	}
}
