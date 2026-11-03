package vn.sunvibe.survey.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import vn.sunvibe.survey.core.infrastructure.media.scan.ScanSamples;

/**
 * The isolation boundary of the processing container (survey-media ADR-011 decision 4 form (a), spec I-7, A-27; plan
 * Task A9; SM-19). The image runs in its production form (no network, read-only root, tmpfs work area, non-root,
 * {@code no-new-privileges}, no capabilities, process and memory limits): code inside cannot read the API's
 * environment, open a connection or write outside the work area. A second container with the same hardening exposes
 * the channel on a host port and starts the supervisor with test-only arguments (short limits, test requests), never
 * part of compose.yaml: a child over its limits is killed alone and reported, work files go, an oversized frame is
 * refused before it is read. Needs Docker; the first run builds the image.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessorIsolationProbeTest {

	private final PostgreSQLContainer database = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private GenericContainer<?> production;
	private GenericContainer<?> channel;

	@BeforeAll
	void start() {
		database.start();
		production = ProcessorImage.hardened(List.of("--listen", "tcp:127.0.0.1:" + ProcessorImage.PORT))
				.withNetworkMode("none")
				.withEnv("SUPERVISOR_MARKER", "production-form")
				.waitingFor(Wait.forLogMessage(".*supervisor listening.*", 1)
						.withStartupTimeout(Duration.ofMinutes(15)));
		production.start();
		channel = ProcessorImage.hardened(List.of("--listen", "tcp:0.0.0.0:" + ProcessorImage.PORT,
						"--enable-test-requests", "--wall-seconds", "3"))
				.withExposedPorts(ProcessorImage.PORT)
				.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(15)));
		channel.start();
	}

	@AfterAll
	void stop() {
		channel.stop();
		production.stop();
		database.stop();
	}

	@Test
	void probeBinary_cannotReadApiEnvironment() throws Exception {
		ExecResult env = production.execInContainer("python3", "-I", "-c",
				"import os; print('\\n'.join(sorted(os.environ)));"
						+ "print(open('/proc/1/environ','rb').read().replace(b'\\0', b'\\n').decode())");

		String names = env.getStdout();
		assertThat(names).contains("SUPERVISOR_MARKER");
		for (String secret : List.of("JWT_SECRET", "DB_PASSWORD", "DB_URL", "SURVEY_MEDIA_S3_ACCESS_KEY_ID",
				"SURVEY_MEDIA_S3_SECRET_ACCESS_KEY", "ID_CARD_CRYPTO_KEY", "ACQUISITION_HASH_KEY", "MAIL_PASSWORD")) {
			assertThat(names).doesNotContain(secret);
		}
	}

	@Test
	void probeBinary_cannotOpenNetworkConnection() throws Exception {
		String databaseAddress = database.getContainerInfo().getNetworkSettings().getNetworks().values().iterator()
				.next().getIpAddress();
		for (String target : List.of(databaseAddress + ":5432", "1.1.1.1:443", "169.254.169.254:80")) {
			String[] hostPort = target.split(":");
			ExecResult connect = production.execInContainer("python3", "-I", "-c",
					"import socket,sys\ntry:\n socket.create_connection(('" + hostPort[0] + "'," + hostPort[1]
							+ "),timeout=3)\n print('CONNECTED')\nexcept OSError as e:\n print('REFUSED')");
			assertEquals("REFUSED", connect.getStdout().trim(), target);
		}
	}

	@Test
	void probeBinary_cannotWriteOutsideWorkDir() throws Exception {
		for (String path : List.of("/probe", "/tmp/probe", "/opt/sv/probe", "/etc/probe")) {
			ExecResult write = production.execInContainer("python3", "-I", "-c",
					"try:\n open('" + path + "','w').write('x')\n print('WROTE')\nexcept OSError:\n print('DENIED')");
			assertEquals("DENIED", write.getStdout().trim(), path);
		}
		ExecResult work = production.execInContainer("python3", "-I", "-c",
				"import tempfile; d=tempfile.mkdtemp(dir='/work'); open(d+'/f','w').write('x'); print('WROTE')");
		assertEquals("WROTE", work.getStdout().trim());
		ExecResult user = production.execInContainer("id", "-u");
		assertEquals("10001", user.getStdout().trim());
	}

	@Test
	void child_exceedingMemory_killedAloneWhileParallelRunCompletes() throws Exception {
		CompletableFuture<Reply> hog = CompletableFuture.supplyAsync(() -> exchange(0x72, new byte[0]));
		CompletableFuture<Reply> normal = CompletableFuture.supplyAsync(() -> exchange(0x01,
				ScanSamples.cleanJpeg()));

		Reply killed = hog.get(60, TimeUnit.SECONDS);
		Reply completed = normal.get(60, TimeUnit.SECONDS);

		assertEquals(Reply.LIMIT_HIT, killed.status(), killed.code());
		assertEquals(Reply.OK, completed.status(), completed.code());
		assertEquals("JPEG", completed.code());
	}

	@Test
	void child_exceedingWallClock_killedAndReportedAsLimitHit() {
		long start = System.nanoTime();

		Reply reply = exchange(0x71, new byte[0]);

		assertEquals(Reply.LIMIT_HIT, reply.status(), reply.code());
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(20));
	}

	@Test
	void workFiles_deletedAfterRunAndAtStart() throws Exception {
		assertEquals(Reply.OK, exchange(0x02, ScanSamples.cleanJpeg()).status());
		assertEquals("", channel.execInContainer("ls", "-A", "/work").getStdout().trim());

		channel.execInContainer("python3", "-I", "-c",
				"import os; os.makedirs('/work/stale-run', mode=0o700); open('/work/stale-run/source','w').write('x')");
		ExecResult sweep = channel.execInContainer("python3", "-I", "/opt/sv/supervisor.py", "--sweep-only");

		assertEquals(0, sweep.getExitCode(), sweep.getStderr());
		assertEquals("", channel.execInContainer("ls", "-A", "/work").getStdout().trim());
	}

	@Test
	void oversizedFrame_refusedBeforeReading() throws IOException {
		try (Socket socket = new Socket(channel.getHost(), channel.getMappedPort(ProcessorImage.PORT))) {
			socket.setSoTimeout(10_000);
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			out.write("SVM1".getBytes(StandardCharsets.US_ASCII));
			out.writeByte(0x02);
			out.writeInt(50 * 1024 * 1024);
			out.flush();

			Reply reply = Reply.read(new DataInputStream(socket.getInputStream()));

			assertEquals(Reply.REFUSED, reply.status());
			assertEquals("FRAME_TOO_LARGE", reply.code());
		}
	}

	@Test
	void normalize_answersThreeVariants() {
		Reply reply = exchange(0x02, ScanSamples.cleanJpeg());

		assertEquals(Reply.OK, reply.status(), reply.code());
		assertEquals(3, reply.variants());
		assertTrue(reply.bytes() > 0);
	}

	private Reply exchange(int op, byte[] body) {
		try (Socket socket = new Socket(channel.getHost(), channel.getMappedPort(ProcessorImage.PORT))) {
			socket.setSoTimeout(60_000);
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			out.write("SVM1".getBytes(StandardCharsets.US_ASCII));
			out.writeByte(op);
			out.writeInt(body.length);
			out.write(body);
			out.flush();
			return Reply.read(new DataInputStream(socket.getInputStream()));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The parts of a reply these tests read (docker/media-processor/PROTOCOL.md). */
	record Reply(int status, String code, int variants, long bytes) {

		static final int OK = 0;
		static final int LIMIT_HIT = 2;
		static final int REFUSED = 4;

		static Reply read(DataInputStream in) throws IOException {
			byte[] magic = in.readNBytes(4);
			assertEquals("SVR1", new String(magic, StandardCharsets.US_ASCII));
			int status = in.readUnsignedByte();
			String code = new String(in.readNBytes(in.readUnsignedByte()), StandardCharsets.US_ASCII);
			int variants = 0;
			long bytes = 0;
			if (status == OK && in.available() >= 0) {
				byte[] rest = in.readAllBytes();
				if (rest.length >= 9) {
					variants = rest[8];
					bytes = rest.length;
				}
			}
			return new Reply(status, code, variants, bytes);
		}
	}
}
