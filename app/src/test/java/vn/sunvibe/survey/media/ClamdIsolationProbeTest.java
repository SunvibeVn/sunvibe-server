package vn.sunvibe.survey.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The clamd boundary of docker/clamav/compose.yaml (survey-media ADR-011 decision 4, spec I-7; plan Task A9; SM-19
 * clamd half): clamd sits on an internal network shared only with the API's worker, holds no API secret and reads its
 * signatures from a read-only mount; only freshclam has egress. Tagged {@code real-clamd}.
 */
@Tag("real-clamd")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClamdIsolationProbeTest {

	private final Network internal = Network.builder().createNetworkCmdModifier(cmd -> cmd.withInternal(true))
			.build();
	private final PostgreSQLContainer database = new PostgreSQLContainer(DockerImageName.parse("postgres:17.6-alpine"));
	private GenericContainer<?> clamd;
	private GenericContainer<?> neighbour;

	@BeforeAll
	void start() throws Exception {
		database.start();
		// The signatures volume of compose.yaml: freshclam's output, mounted read-only into clamd
		java.nio.file.Path signatures = java.nio.file.Files.createTempDirectory("clamav-signatures");
		try (GenericContainer<?> seed = new GenericContainer<>(RealClamdScannerTest.CLAMAV)
				.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sleep", "600"))) {
			seed.start();
			for (String file : seed.execInContainer("ls", "/var/lib/clamav").getStdout().trim().split("\\s+")) {
				seed.copyFileFromContainer("/var/lib/clamav/" + file, signatures.resolve(file).toString());
			}
		}
		// As docker/clamav/compose.yaml: clamd started directly with its config, signatures read-only
		java.nio.file.Path config = java.nio.file.Path.of(System.getProperty("user.dir")).resolveSibling("docker")
				.resolve("clamav").resolve("clamd.conf");
		clamd = new GenericContainer<>(RealClamdScannerTest.CLAMAV)
				.withNetwork(internal)
				.withNetworkAliases("clamd")
				.withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("clamd", "--foreground",
						"--config-file=/etc/clamav-sunvibe/clamd.conf"))
				.withCopyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath(config),
						"/etc/clamav-sunvibe/clamd.conf")
				.withTmpFs(java.util.Map.of("/run/clamav", "rw", "/tmp", "rw"))
				.withFileSystemBind(signatures.toString(), "/var/lib/clamav", BindMode.READ_ONLY)
				.waitingFor(Wait.forSuccessfulCommand(
						"clamdscan --ping 1 --config-file=/etc/clamav-sunvibe/clamd.conf")
						.withStartupTimeout(Duration.ofMinutes(5)));
		clamd.start();
		neighbour = new GenericContainer<>(DockerImageName.parse("alpine:3.20"))
				.withNetwork(internal)
				.withCommand("sleep", "3600");
		neighbour.start();
	}

	@AfterAll
	void stop() {
		if (neighbour != null) {
			neighbour.stop();
		}
		if (clamd != null) {
			clamd.stop();
		}
		database.stop();
		internal.close();
	}

	@Test
	void probeInClamdNetwork_cannotReachOutsideDatabaseOrMetadata() throws Exception {
		String databaseAddress = database.getContainerInfo().getNetworkSettings().getNetworks().values().iterator()
				.next().getIpAddress();
		for (String target : List.of(databaseAddress + " 5432", "1.1.1.1 443", "169.254.169.254 80")) {
			ExecResult connect = neighbour.execInContainer("sh", "-c", "nc -z -w 3 " + target
					+ " && echo CONNECTED || echo REFUSED");
			assertEquals("REFUSED", connect.getStdout().trim(), target);
		}
		ExecResult clamdPort = neighbour.execInContainer("sh", "-c", "nc -z -w 3 clamd 3310 && echo CONNECTED");
		assertEquals("CONNECTED", clamdPort.getStdout().trim());
	}

	@Test
	void probeInClamdContainer_cannotReadApiEnvironment() throws Exception {
		ExecResult env = clamd.execInContainer("env");

		for (String secret : List.of("JWT_SECRET", "DB_PASSWORD", "SURVEY_MEDIA_S3_ACCESS_KEY_ID",
				"SURVEY_MEDIA_S3_SECRET_ACCESS_KEY")) {
			assertThat(env.getStdout()).doesNotContain(secret);
		}
	}

	@Test
	void clamdSignatures_mountedReadOnly() throws Exception {
		ExecResult write = clamd.execInContainer("sh", "-c",
				"touch /var/lib/clamav/probe 2>/dev/null && echo WROTE || echo DENIED");

		assertEquals("DENIED", write.getStdout().trim());
	}
}
