package vn.sunvibe.survey.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner.ScanVerdict;
import vn.sunvibe.survey.core.infrastructure.media.scan.ClamAvScanner;
import vn.sunvibe.survey.core.infrastructure.media.scan.ScanSamples;

/**
 * The ClamAV adapter against a real {@code clamd} (survey-media spec A-15; plan PD-7): EICAR is FOUND, a clean JPEG is
 * OK, VERSION carries a signature date. The image loads its signatures at start, so this is slow and tagged:
 * {@code ./gradlew :app:realClamdTest}.
 */
@Tag("real-clamd")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RealClamdScannerTest {

	static final DockerImageName CLAMAV = DockerImageName.parse(
			"clamav/clamav:1.5.4@sha256:6d87c712e3391e13c1ba6e05dfb074d4b1ddb5a6834b23bb9da990d2a4ef042a");

	private final GenericContainer<?> clamd = new GenericContainer<>(CLAMAV)
			.withExposedPorts(3310)
			.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(10)));
	private ClamAvScanner scanner;

	@BeforeAll
	void start() {
		clamd.start();
		scanner = new ClamAvScanner(clamd.getHost(), clamd.getMappedPort(3310), null, Duration.ofSeconds(60));
	}

	@AfterAll
	void stop() {
		clamd.stop();
	}

	@Test
	void eicar_isFound() {
		assertEquals(ScanVerdict.FOUND, scanner.scan(new ByteArrayInputStream(ScanSamples.eicar())));
	}

	@Test
	void cleanJpeg_isOk() {
		assertEquals(ScanVerdict.CLEAN, scanner.scan(new ByteArrayInputStream(ScanSamples.cleanJpeg())));
	}

	@Test
	void version_hasASignatureDate() {
		assertNotNull(scanner.signatureDate());
	}
}
