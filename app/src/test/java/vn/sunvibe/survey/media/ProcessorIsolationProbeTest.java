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
 * Task A9; SM-19). The image runs in its production form (no network, read-only root, tmpfs work area,
 * {@code no-new-privileges}, process and memory limits; the supervisor keeps only SETUID, SETGID and CHOWN): code in a
 * run, as its slot's uid, cannot read the API's environment, open a connection, write outside its slot's directory or
 * read another run's files, and a process it leaves behind is ended before the slot runs again (security review M-1,
 * N-2, SV-069). A second container with the same hardening exposes
 * the channel on a host port and starts the supervisor with test-only arguments (short limits, test requests), never
 * part of compose.yaml: a child over its limits is killed alone and reported, work files go, an oversized frame is
 * refused before it is read. Needs Docker; the first run builds the image.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessorIsolationProbeTest {

	/** A 1 x 1 PNG. */
	private static final String PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

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
		ExecResult env = production.execInContainerWithUser(String.valueOf(ProcessorImage.SLOT_UID) + ":" + ProcessorImage.SLOT_UID, "python3", "-I", "-c",
				"import os; print('\\n'.join(sorted(os.environ)));"
						+ "print(open('/proc/1/environ','rb').read().replace(b'\\0', b'\\n').decode())");

		String names = env.getStdout();
		assertThat(names).contains("SUPERVISOR_MARKER");
		for (String secret : List.of("JWT_SECRET", "DB_PASSWORD", "DB_URL", "SURVEY_MEDIA_S3_ACCESS_KEY_ID",
				"SURVEY_MEDIA_S3_SECRET_ACCESS_KEY", "ID_CARD_CRYPTO_KEY", "ACQUISITION_HASH_KEY", "AUTH_OTP_HASH_KEY", "MAIL_PASSWORD")) {
			assertThat(names).doesNotContain(secret);
		}
	}

	@Test
	void probeBinary_cannotOpenNetworkConnection() throws Exception {
		String databaseAddress = database.getContainerInfo().getNetworkSettings().getNetworks().values().iterator()
				.next().getIpAddress();
		for (String target : List.of(databaseAddress + ":5432", "1.1.1.1:443", "169.254.169.254:80")) {
			String[] hostPort = target.split(":");
			ExecResult connect = production.execInContainerWithUser(String.valueOf(ProcessorImage.SLOT_UID) + ":" + ProcessorImage.SLOT_UID, "python3", "-I", "-c",
					"import socket,sys\ntry:\n socket.create_connection(('" + hostPort[0] + "'," + hostPort[1]
							+ "),timeout=3)\n print('CONNECTED')\nexcept OSError as e:\n print('REFUSED')");
			assertEquals("REFUSED", connect.getStdout().trim(), target);
		}
	}

	@Test
	void probeBinary_cannotWriteOutsideWorkDir() throws Exception {
		for (String path : List.of("/probe", "/tmp/probe", "/opt/sv/probe", "/etc/probe", "/work/probe",
				"/work/slot-2/probe", "/dev/shm/probe")) {
			ExecResult write = production.execInContainerWithUser(String.valueOf(ProcessorImage.SLOT_UID) + ":" + ProcessorImage.SLOT_UID, "python3", "-I", "-c",
					"try:\n open('" + path + "','w').write('x')\n print('WROTE')\nexcept OSError:\n print('DENIED')");
			assertEquals("DENIED", write.getStdout().trim(), path);
		}
		ExecResult work = production.execInContainerWithUser(String.valueOf(ProcessorImage.SLOT_UID) + ":" + ProcessorImage.SLOT_UID, "python3", "-I", "-c",
				"open('/work/slot-1/f','w').write('x'); print('WROTE')");
		assertEquals("WROTE", work.getStdout().trim(), work.getStderr());
		ExecResult user = production.execInContainerWithUser(String.valueOf(ProcessorImage.SLOT_UID) + ":" + ProcessorImage.SLOT_UID, "python3", "-I", "-c",
				"import os; print(os.getuid(), open('/proc/self/status').read().split('CapEff:')[1].split()[0])");
		assertEquals(ProcessorImage.SLOT_UID + " 0000000000000000", user.getStdout().trim(), "no capability");
	}

	/** security review M-1 (SV-069, re-review N1): no System V shared memory, queue or semaphore outlives a reset. */
	@Test
	void probeBinary_cannotCreateSystemVIpcObjects() throws Exception {
		ExecResult ipc = production.execInContainerWithUser(String.valueOf(ProcessorImage.SLOT_UID) + ":"
						+ ProcessorImage.SLOT_UID, "python3", "-I", "-c",
				"import ctypes\nc=ctypes.CDLL(None, use_errno=True)\n"
						+ "print(c.shmget(0, 4096, 0o1600), c.msgget(0, 0o1600), c.semget(0, 1, 0o1600))");

		assertEquals("-1 -1 -1", ipc.getStdout().trim(), ipc.getStderr());
	}

	/** security review M-1 (SV-069): each run slot has its own uid and 0700 directory; no run reads another's files. */
	@Test
	void runSlots_ownUidAndDirectory_noRunReadsAnothersFiles() throws Exception {
		ExecResult owners = channel.execInContainer("python3", "-I", "-c",
				"import os,stat\nfor n in (1,2,3):\n s=os.lstat('/work/slot-%d'%n)\n"
						+ " print(n, s.st_uid, oct(stat.S_IMODE(s.st_mode)))");
		assertEquals("1 10101 0o700\n2 10102 0o700\n3 10103 0o700", owners.getStdout().trim(), owners.getStderr());

		String before = slotTimes();
		CompletableFuture<Reply> sleeping = CompletableFuture.supplyAsync(() -> exchange(0x71,
				"source of a sleeping run".getBytes(StandardCharsets.US_ASCII)));
		try {
			String holder = awaitSourceHolder(before);
			for (int slot = 1; slot <= 3; slot++) {
				String uid = String.valueOf(ProcessorImage.SLOT_UID + slot - 1);
				if (uid.equals(holder)) {
					continue;
				}
				ExecResult read = channel.execInContainerWithUser(uid + ":" + uid, "python3", "-I", "-c",
						"import os\ntry:\n print(os.listdir('/work/slot-" + (Integer.parseInt(holder)
								- ProcessorImage.SLOT_UID + 1) + "'))\nexcept OSError:\n print('DENIED')");
				assertEquals("DENIED", read.getStdout().trim(), "slot uid " + uid);
			}
		} finally {
			assertEquals(Reply.LIMIT_HIT, sleeping.get(60, TimeUnit.SECONDS).status());
		}
	}

	/** security review M-1 (SV-069): a process a run leaves behind is ended before its slot's uid runs again. */
	@Test
	void processLeftByRun_endedWhenTheRunEnds() throws Exception {
		assertEquals(Reply.OK, exchange(0x73, new byte[0]).status());

		assertEquals("0", slotProcesses(), "no process of a slot uid is left");
	}

	/** The processes of the slot uids, once the container's init had a moment to reap the killed ones. */
	private String slotProcesses() throws Exception {
		String left = "";
		for (int attempt = 0; attempt < 50 && !left.equals("0"); attempt++) {
			left = channel.execInContainer("python3", "-I", "-c",
					"import os\nn=0\nfor p in os.listdir('/proc'):\n if p.isdigit():\n  try:\n"
							+ "   uid=int(open('/proc/'+p+'/status').read().split('Uid:')[1].split()[0])\n"
							+ "  except OSError:\n   continue\n  if 10101<=uid<=10103:\n   n+=1\nprint(n)")
					.getStdout().trim();
			Thread.sleep(100);
		}
		return left;
	}

	/**
	 * security review M-1 (SV-069, re-review): a leftover that kills every other process of its uid, the reset helper
	 * included, is still ended: the supervisor kills the slot's processes itself before the helper starts.
	 */
	@Test
	void leftoverKillingTheResetHelper_stillEndedBeforeTheAnswer() throws Exception {
		Reply reply = exchange(0x75, new byte[0]);

		assertEquals(Reply.OK, reply.status(), reply.code());
		assertEquals("0", slotProcesses(), "no process of a slot uid is left");
		assertEquals(Reply.OK, exchange(0x02, ScanSamples.cleanJpeg()).status(), "the supervisor still answers");
	}

	/** security review M-1 (SV-069, re-review): files left 1,500 directories deep, under a closed one, are deleted. */
	@Test
	void filesLeftDeepUnderAClosedDirectory_deletedBeforeTheAnswer() throws Exception {
		assertEquals(Reply.OK, exchange(0x76, new byte[0]).status());

		assertEquals("", slotFiles());
	}

	/** security review N-2 (SV-069): a result the supervisor cannot pack answers FAILED BAD_OUTPUT, never no answer. */
	@Test
	void malformedChildResult_failedBadOutput() {
		Reply reply = exchange(0x74, new byte[0]);

		assertEquals(Reply.FAILED, reply.status(), reply.code());
		assertEquals("BAD_OUTPUT", reply.code());
	}

	/** security review M-2 (SV-069): the run refuses a file libvips would open with another loader than its format's. */
	@Test
	void loaderOfAnotherFormat_doesNotMatchTheDetectedFormat() throws Exception {
		ExecResult loaders = production.execInContainerWithUser(String.valueOf(ProcessorImage.SLOT_UID) + ":" + ProcessorImage.SLOT_UID, "python3", "-I", "-c",
				"import base64,sys\nsys.path.insert(0,'/opt/sv')\nimport pipeline\n"
						+ "open('/work/slot-1/p.png','wb').write(base64.b64decode('" + PNG_1X1 + "'))\n"
						+ "print(pipeline.loader_matches('PNG','/work/slot-1/p.png'),"
						+ " pipeline.loader_matches('JPEG','/work/slot-1/p.png'))");

		assertEquals("True False", loaders.getStdout().trim(), loaders.getStderr());
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
		assertEquals("slot-1\nslot-2\nslot-3", channel.execInContainer("ls", "-A", "/work").getStdout().trim());
		assertEquals("", slotFiles());

		channel.execInContainer("python3", "-I", "-c",
				"import os; os.makedirs('/work/stale-run', mode=0o700); open('/work/stale-run/source','w').write('x')");
		channel.execInContainerWithUser(String.valueOf(ProcessorImage.SLOT_UID) + ":" + ProcessorImage.SLOT_UID, "python3", "-I", "-c",
				"import os; os.makedirs('/work/slot-1/left/deep', mode=0o700); os.chmod('/work/slot-1/left', 0)");
		ExecResult sweep = channel.execInContainer("python3", "-I", "/opt/sv/supervisor.py", "--sweep-only",
				"--enable-test-requests");

		assertEquals(0, sweep.getExitCode(), sweep.getStderr());
		assertEquals("slot-1\nslot-2\nslot-3", channel.execInContainer("ls", "-A", "/work").getStdout().trim());
		assertEquals("", slotFiles());
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

	/** The entries of every slot directory, each listed as its own uid. */
	private String slotFiles() throws Exception {
		StringBuilder files = new StringBuilder();
		for (int slot = 1; slot <= 3; slot++) {
			String uid = String.valueOf(ProcessorImage.SLOT_UID + slot - 1);
			files.append(channel.execInContainerWithUser(uid + ":" + uid, "ls", "-A", "/work/slot-" + slot)
					.getStdout().trim());
		}
		return files.toString();
	}

	/** The modification times of the slot directories, read as root (stat needs no access to their content). */
	private String slotTimes() throws Exception {
		return channel.execInContainer("python3", "-I", "-c",
				"import os; print(' '.join(str(os.stat('/work/slot-%d' % n).st_mtime_ns) for n in (1, 2, 3)))")
				.getStdout().trim();
	}

	/**
	 * The uid of the slot whose directory changed since {@code before}: the one a sleeping run wrote its source into.
	 * Watched as root, so no process of a slot uid races the slot's reset.
	 */
	private String awaitSourceHolder(String before) throws Exception {
		String[] was = before.split(" ");
		long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
		while (System.nanoTime() < deadline) {
			String[] now = slotTimes().split(" ");
			for (int slot = 1; slot <= 3; slot++) {
				if (now.length == 3 && !now[slot - 1].equals(was[slot - 1])) {
					return String.valueOf(ProcessorImage.SLOT_UID + slot - 1);
				}
			}
			Thread.sleep(100);
		}
		throw new AssertionError("no run wrote its source: " + channelLog());
	}

	/** The end of the channel container's log (the supervisor's lines), for a failure message. */
	private String channelLog() {
		String log = channel.getLogs();
		return log.substring(Math.max(0, log.length() - 2000));
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
			try {
				return Reply.read(new DataInputStream(socket.getInputStream()));
			} catch (AssertionError e) {
				throw new AssertionError("no answer to op " + op + "; supervisor log: " + channelLog(), e);
			}
		} catch (IOException e) {
			throw new IllegalStateException("op " + op + "; supervisor log: " + channelLog(), e);
		}
	}

	/** The parts of a reply these tests read (docker/media-processor/PROTOCOL.md). */
	record Reply(int status, String code, int variants, long bytes) {

		static final int OK = 0;
		static final int LIMIT_HIT = 2;
		static final int FAILED = 3;
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
