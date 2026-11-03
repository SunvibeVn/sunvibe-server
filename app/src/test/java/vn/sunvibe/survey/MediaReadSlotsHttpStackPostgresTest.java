package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import vn.sunvibe.PostgresContainerConfig;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.Result;
import vn.sunvibe.acquisition.AcquisitionApiTestSupport.TestUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.survey.ChecklistApiTestSupport.Visit;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;

/**
 * Content GETs through the real HTTP stack with a Hikari pool of two connections (survey-media spec A-25, ADR-011
 * decision 2; SM-20 read half; plan Task A17): slow readers hold no connection, the read slots refuse at once without
 * a store call, a stalled stream's store stream is closed after the no-progress timeout and a slow FULL at its
 * deadline (both set short here: the context of {@code MediaUploadHttpStackPostgresTest}). The GET client's own pool
 * and its 2 s acquisition timeout, and a probe completing while that pool is exhausted, are proven at the adapter by
 * {@code MinioMediaObjectStoreTest}. Needs Docker.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
			"spring.flyway.enabled=true",
			"spring.jpa.hibernate.ddl-auto=validate",
			"app.security.rate-limit.enabled=false",
			MediaApiTestBase.STORE,
			MediaApiTestBase.NO_BACKGROUND_SWEEP,
			MediaApiTestBase.NO_BACKGROUND_SWEEP_INTERVAL,
			MediaApiTestBase.NO_BACKGROUND_PROBES,
			MediaApiTestBase.NO_BACKGROUND_DELETION,
			MediaApiTestBase.NO_BACKGROUND_LISTING,
			MediaApiTestBase.STORE_ROOT,
			MediaApiTestBase.SPOOL_DIR,
			"spring.datasource.hikari.maximum-pool-size=2",
			"spring.datasource.hikari.connection-timeout=10000",
			MediaApiTestBase.NO_PROGRESS,
			MediaApiTestBase.FULL_DEADLINE })
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, MediaTestSwitches.Config.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MediaReadSlotsHttpStackPostgresTest {

	private static final byte[] BIG_FULL = MediaApiTestSupport.bytes(8 * 1024 * 1024, 41);
	private static final byte[] PREVIEW = MediaApiTestSupport.bytes(1_000, 42);
	private static final byte[] THUMBNAIL = MediaApiTestSupport.bytes(500, 43);

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
	private TestUser leader;
	private final List<TestUser> readers = new ArrayList<>();
	private final List<String> readerChecklists = new ArrayList<>();
	private final List<Socket> open = new ArrayList<>();

	@BeforeAll
	void setUp() throws Exception {
		AcquisitionApiTestSupport api = new AcquisitionApiTestSupport(port, userRepository, passwordEncoder,
				ulidGenerator);
		survey = new SurveyApiTestSupport(api, jdbc, "0983");
		checklists = new ChecklistApiTestSupport(survey, jdbc, ulidGenerator::generateId);
		media = new MediaApiTestSupport(checklists, jdbc);
		leader = survey.user("Slots Leader", "SALE_LEADER");
		for (int i = 0; i < 6; i++) {
			TestUser reader = survey.user("Slots Reader " + i, "SALE");
			readers.add(reader);
			Visit visit = checklists.visited(leader, reader);
			readerChecklists.add(checklists.started(reader, visit.appointmentId()));
		}
	}

	@AfterEach
	void closeReaders() throws Exception {
		for (Socket socket : open) {
			socket.close();
		}
		open.clear();
		store.reset();
		Thread.sleep(500);
	}

	@AfterAll
	void tearDown() {
		survey.cleanUp();
	}

	@Test
	void thirtyTwoSlowReaders_holdNoConnection_loginAndLeadAnswer_thirtyThirdGet503AtOnceNoStoreCall()
			throws Exception {
		for (int user = 0; user < 6; user++) {
			for (int n = 0; n < (user < 5 ? 6 : 2); n++) {
				stall(user);
			}
		}
		awaitServingReads(32);

		TestUser fresh = checklists.api().createUser("Slots Fresh Login", UserStatus.ACTIVE, "SALE");
		try {
			long loginStart = System.nanoTime();
			checklists.api().logIn(fresh);
			long loginMillis = Duration.ofNanos(System.nanoTime() - loginStart).toMillis();
			long leadsStart = System.nanoTime();
			Result leads = checklists.api().get(leader, SurveyApiTestSupport.LEADS);
			long leadsMillis = Duration.ofNanos(System.nanoTime() - leadsStart).toMillis();
			// SV-066: the timings are recorded in the test output
			System.out.println("SV-066 while 32 reads stall: login " + loginMillis + " ms, lead list " + leadsMillis
					+ " ms");
			assertEquals(200, leads.status(), leads.rawBody());
			assertTrue(loginMillis < 5_000 && leadsMillis < 5_000, loginMillis + " / " + leadsMillis);
		} finally {
			AcquisitionApiTestSupport.deleteUsers(jdbc, List.of(fresh.userId()));
		}

		String extra = ready(5);
		Map<String, String> refused = rawGet(readers.get(5), extra, false);
		assertEquals("503", refused.get(":status"), refused.toString());
		assertTrue(Long.parseLong(refused.get("retry-after")) > 0);
		assertEquals(32, store.servingReads.get(), "the 33rd read made no store call");
	}

	@Test
	void seventhGetOfUser_429ConcurrencyNoStoreCall() throws Exception {
		for (int n = 0; n < 6; n++) {
			stall(0);
		}
		awaitServingReads(6);

		String seventh = ready(0);
		Map<String, String> refused = rawGet(readers.get(0), seventh, false);

		assertEquals("429", refused.get(":status"), refused.toString());
		assertTrue(Long.parseLong(refused.get("retry-after")) > 0);
		assertEquals(6, store.servingReads.get());
	}

	@Test
	void stalledStream_closedAfterNoProgress_r2StreamReleased() throws Exception {
		stall(1);
		awaitServingReads(1);

		long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
		while (store.openServingStreams.get() > 0 && System.nanoTime() < deadline) {
			Thread.sleep(100);
		}

		assertEquals(0, store.openServingStreams.get(), "the store stream is closed after 2 s without progress");
	}

	/**
	 * spec A-25 (code review I1, security review I-2): the watchdog ends a stalled read's store stream, but the read's
	 * slots stay taken while its request thread is still blocked writing to the client, so the caps bound the blocked
	 * threads.
	 */
	@Test
	void stalledClients_slotsHeldWhileRequestThreadsBlocked_seventhStill429() throws Exception {
		for (int n = 0; n < 6; n++) {
			stall(4);
		}
		awaitServingReads(6);
		Thread.sleep(3_500);
		assertEquals(0, store.openServingStreams.get(), "the store streams were ended after 2 s without progress");

		String seventh = ready(4);
		Map<String, String> refused = rawGet(readers.get(4), seventh, false);

		assertEquals("429", refused.get(":status"), "the blocked request threads keep their slots: " + refused);
	}

	/**
	 * spec A-25: a FULL whose store stream still makes progress (64 KB per 200 ms) is ended at its 6 s deadline, with
	 * its store stream. The client reads at once, so the end it sees is the server's: a slow client would see the
	 * bytes the kernel had already accepted long after the server stopped (about 2 MB on a Linux loopback, SV-068), and
	 * the server would stop it by the 2 s no-progress timeout before the deadline.
	 */
	@Test
	void slowFull_closedAtDeadline() throws Exception {
		String id = ready(2);
		store.slowServing.set(true);
		long start = System.nanoTime();
		long read = 0;
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("localhost", port));
			socket.setSoTimeout(15_000);
			socket.getOutputStream().write(("GET " + MediaApiTestSupport.MEDIA + "/" + id + "/content/full HTTP/1.1\r\n"
					+ "Host: localhost\r\nAuthorization: Bearer " + readers.get(2).secret() + "\r\n\r\n")
					.getBytes(StandardCharsets.US_ASCII));
			InputStream in = socket.getInputStream();
			byte[] chunk = new byte[64 * 1024];
			try {
				for (int n = in.read(chunk); n != -1; n = in.read(chunk)) {
					read += n;
				}
			} catch (IOException e) {
				// the server closed the stream
			}
		}
		long clientMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

		assertTrue(read < BIG_FULL.length, "stopped before the whole variant: " + read);
		assertEquals(1, store.served.size(), "one store stream was opened");
		MediaTestStore.ServedStream served = store.served.get(0);
		assertTrue(served.closed(), "the store stream was closed");
		long closedMillis = Duration.ofNanos(served.closedAtNanos() - start).toMillis();
		assertTrue(closedMillis >= 5_000 && closedMillis <= 10_000,
				"the store stream was closed at the 6 s deadline, not by the no-progress timeout: " + closedMillis);
		assertTrue(clientMillis >= 5_000 && clientMillis <= 12_000, "the answer ended at the deadline: " + clientMillis);
	}

	@Test
	void noGetConnectionWithin2Seconds_503StoreUnavailableNoRow() throws Exception {
		String id = ready(3);
		store.failServing.set(true);

		Result refused = media.read(readers.get(3), id, "preview");

		assertEquals(503, refused.status(), refused.rawBody());
		assertEquals("MEDIA_STORE_UNAVAILABLE", refused.body().path("code").asString());
		assertEquals(0, jdbc.queryForObject("select count(*) from survey_access_logs where resource_id = ?",
				Integer.class, id));
	}

	@Test
	void probeCompletes_whileReadSlotsAreFull() throws Exception {
		for (int n = 0; n < 6; n++) {
			stall(4);
		}
		awaitServingReads(6);

		long start = System.nanoTime();
		store.probe(MediaObjectStore.Bucket.MEDIA);

		assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 2);
	}

	// ==================== helpers ====================

	private String ready(int user) {
		return media.readyWithVariants(store, readerChecklists.get(user), readers.get(user).userId(), BIG_FULL, PREVIEW,
				THUMBNAIL);
	}

	/** A FULL read of a new photo of the user that reads nothing, so the server blocks while writing. */
	private void stall(int user) throws Exception {
		String id = ready(user);
		Socket socket = new Socket();
		socket.setReceiveBufferSize(4 * 1024);
		socket.connect(new InetSocketAddress("localhost", port));
		open.add(socket);
		socket.getOutputStream().write(("GET " + MediaApiTestSupport.MEDIA + "/" + id + "/content/full HTTP/1.1\r\n"
				+ "Host: localhost\r\nAuthorization: Bearer " + readers.get(user).secret() + "\r\n\r\n")
				.getBytes(StandardCharsets.US_ASCII));
		socket.getOutputStream().flush();
	}

	private void awaitServingReads(int expected) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		while (store.servingReads.get() < expected && System.nanoTime() < deadline) {
			Thread.sleep(50);
		}
		assertEquals(expected, store.servingReads.get());
	}

	private Map<String, String> rawGet(TestUser caller, String id, boolean ignored) throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			socket.setSoTimeout(5_000);
			socket.getOutputStream().write(("GET " + MediaApiTestSupport.MEDIA + "/" + id + "/content/full HTTP/1.1\r\n"
					+ "Host: localhost\r\nAuthorization: Bearer " + caller.secret() + "\r\n\r\n")
					.getBytes(StandardCharsets.US_ASCII));
			InputStream in = socket.getInputStream();
			Map<String, String> head = new LinkedHashMap<>();
			StringBuilder line = new StringBuilder();
			int previous = -1;
			for (int next = in.read(); next != -1; next = in.read()) {
				if (next == '\n' && previous == '\r') {
					String text = line.substring(0, line.length() - 1);
					line.setLength(0);
					if (text.isEmpty()) {
						break;
					}
					if (head.isEmpty()) {
						head.put(":status", text.split(" ")[1]);
					} else {
						int colon = text.indexOf(':');
						head.put(text.substring(0, colon).trim().toLowerCase(Locale.ROOT), text.substring(colon + 1)
								.trim());
					}
				} else {
					line.append((char) next);
				}
				previous = next;
			}
			return head;
		}
	}
}
