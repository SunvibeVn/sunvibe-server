package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

/**
 * The access log of the media list (survey-media spec A-6, A-19, 4.9; SM-21 list part; plan Task A16): one MEDIA_LIST
 * row per user and checklist per 10 minutes of list reads, a 204 included, each with the server request id; refused
 * reads write throttled events and no rows; rows and events hold ids only. Needs Docker.
 */
class MediaAccessLogPostgresTest extends MediaApiTestBase {

	private static final String ULID = "[0-9A-HJKMNP-TV-Z]{26}";

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

	private SurveyApiTestSupport survey;
	private ChecklistApiTestSupport checklists;
	private MediaApiTestSupport media;
	private TestUser sale;
	private TestUser otherSale;
	private TestUser leader;
	private final ExecutorService pool = Executors.newFixedThreadPool(32);

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0981");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		sale = survey.user("Access Sale", "SALE");
		otherSale = survey.user("Access Other Sale", "SALE");
		leader = survey.user("Access Leader", "SALE_LEADER");
	}

	@AfterAll
	void tearDown() {
		pool.shutdownNow();
		survey.cleanUp();
	}

	@Test
	void polling_writesOneListRowPer10Minutes_204Included() throws Exception {
		Draft draft = draft();
		JsonNode first = ok(media.list(sale, draft.id(), null));
		String known = MediaApiTestSupport.knownVersion(first);

		assertEquals(204, media.list(sale, draft.id(), known).status());
		assertEquals(1, listRows(draft).size());

		jdbc.update("update survey_access_logs set accessed_at = ? where user_id = ? and resource_type = 'MEDIA_LIST'"
				+ " and resource_id = ?", Timestamp.from(Instant.now().minus(Duration.ofMinutes(11))), sale.userId(),
				draft.id());
		assertEquals(204, media.list(sale, draft.id(), known).status());

		assertEquals(2, listRows(draft).size(), "the 204 poll after 10 minutes is a list read too");
	}

	@Test
	void rowsCarryRequestId() throws Exception {
		Draft draft = draft();

		ok(media.list(sale, draft.id(), null));

		Map<String, Object> row = listRows(draft).get(0);
		assertTrue(((String) row.get("request_id")).matches(ULID), String.valueOf(row.get("request_id")));
		assertEquals(draft.visit().leadId(), row.get("lead_id"));
	}

	@Test
	void deniedReads_writeEventsNotRows() throws Exception {
		Draft draft = draft();

		assertEquals(404, media.list(otherSale, draft.id(), null).status());

		assertEquals(0, jdbc.queryForObject("select count(*) from survey_access_logs where user_id = ? and"
				+ " resource_type = 'MEDIA_LIST'", Integer.class, otherSale.userId()));
		assertEquals(1, media.mediaEvents(draft.visit().leadId()).size());
	}

	@Test
	void scriptDenied1000TimesInParallel_oneEventPerKey() throws Exception {
		Draft draft = draft();
		List<Callable<Result>> calls = new ArrayList<>();
		for (int i = 0; i < 1000; i++) {
			calls.add(() -> media.list(otherSale, draft.id(), null));
		}

		for (Future<Result> future : pool.invokeAll(calls, 300, TimeUnit.SECONDS)) {
			assertEquals(404, future.get().status());
		}

		List<Map<String, Object>> events = media.mediaEvents(draft.visit().leadId());
		assertEquals(1, events.size(), "spec A-19: one event per user, resource, action and outcome a minute");
	}

	@Test
	void noIpUserAgentNamePathOrHashInRows() throws Exception {
		Draft draft = draft();
		ok(media.list(sale, draft.id(), null));
		media.list(otherSale, draft.id(), null);

		for (String table : List.of("survey_access_logs", "survey_security_events")) {
			List<String> columns = jdbc.queryForList("select column_name from information_schema.columns where"
					+ " table_name = ?", String.class, table);
			for (String column : columns) {
				String name = column.toLowerCase(Locale.ROOT);
				for (String banned : List.of("ip", "agent", "path", "hash", "name", "url")) {
					assertFalse(name.equals(banned) || name.contains("_" + banned) || name.startsWith(banned + "_"),
							table + "." + column);
				}
			}
		}
		Map<String, Object> row = listRows(draft).get(0);
		for (Object value : row.values()) {
			assertFalse(String.valueOf(value).contains("/"), "ids only: " + row);
		}
	}

	// ==================== content reads (SM-21) ====================

	@Test
	void previewFullDownload_oneRowEachWithRequestId() throws Exception {
		Draft draft = draft();
		String id = ready(draft);

		for (String variant : List.of("preview", "full", "full?download=true")) {
			assertEquals(200, media.read(sale, id, variant).status(), variant);
		}

		List<Map<String, Object>> rows = jdbc.queryForList("select resource_type, request_id from survey_access_logs"
				+ " where resource_id = ? order by accessed_at, id", id);
		assertEquals(List.of("MEDIA_PREVIEW", "MEDIA_FULL", "MEDIA_DOWNLOAD"), rows.stream()
				.map(row -> row.get("resource_type")).toList());
		assertTrue(rows.stream().allMatch(row -> ((String) row.get("request_id")).matches(ULID)));
	}

	@Test
	void thumbnail_writesRowOnlyWithoutRecentListRow() throws Exception {
		Draft draft = draft();
		String id = ready(draft);

		assertEquals(200, media.read(sale, id, "thumbnail").status());
		assertEquals(200, media.read(sale, id, "thumbnail").status());
		assertEquals(1, listRows(draft).size(), "the first thumbnail writes the list row; the next is covered");
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_access_logs where resource_id = ?",
				Integer.class, id));
	}

	@Test
	void storeFailureBeforeFirstByte_writesNoRow() throws Exception {
		Draft draft = draft();
		String id = ready(draft);
		store.failServing.set(true);
		try {
			assertEquals(503, media.read(sale, id, "preview").status());
		} finally {
			store.reset();
		}

		assertEquals(0, jdbc.queryForObject("select count(*) from survey_access_logs where resource_id = ?",
				Integer.class, id));
	}

	// ==================== helpers ====================

	private record Draft(Visit visit, String id) {
	}

	private Draft draft() throws Exception {
		Visit visit = checklists.visited(leader, sale);
		return new Draft(visit, checklists.started(sale, visit.appointmentId()));
	}

	private String ready(Draft draft) {
		return media.readyWithVariants(store, draft.id(), sale.userId(), MediaApiTestSupport.bytes(3000, 7),
				MediaApiTestSupport.bytes(2000, 8), MediaApiTestSupport.bytes(1000, 9));
	}

	private List<Map<String, Object>> listRows(Draft draft) {
		return jdbc.queryForList("select * from survey_access_logs where resource_type = 'MEDIA_LIST' and"
				+ " resource_id = ? order by accessed_at", draft.id());
	}

	private static JsonNode ok(Result result) {
		assertEquals(200, result.status(), result.rawBody());
		return result.body();
	}
}
