package vn.sunvibe.survey.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import vn.sunvibe.survey.core.application.shared.external.media.MalwareScanner.ScanVerdict;
import vn.sunvibe.survey.core.application.shared.external.media.ScannerSilentException;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.infrastructure.media.scan.ClamAvScanner;

/**
 * The ClamAV adapter against a scripted clamd (survey-media spec 4.5 scan row, A-7, A-18; SM-3 adapter part): clean
 * only on OK, a limit or error line is a per-file condition, no answer is silence, the probe's four checks, and no
 * signature name in any log.
 */
class ClamAvScannerTest {

	private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

	private FakeClamd clamd;
	private ClamAvScanner scanner;
	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	@BeforeEach
	void start() throws IOException {
		clamd = new FakeClamd();
		scanner = new ClamAvScanner("127.0.0.1", clamd.port(), null, Duration.ofSeconds(2));
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		logs.setContext(context);
		logs.start();
		context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(logs);
	}

	@AfterEach
	void stop() throws IOException {
		((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
				.detachAppender(logs);
		clamd.close();
	}

	@Test
	void found_isFoundWithoutLoggingName() {
		clamd.script(FakeClamd.Script.FOUND).foundName("Win.Trojan.SecretName-1234");

		ScanVerdict verdict = scanner.scan(sample("anything"));

		assertEquals(ScanVerdict.Kind.FOUND, verdict.kind());
		assertThat(verdict.toString()).doesNotContain("SecretName");
		assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains("SecretName"));
	}

	@Test
	void ok_isClean() {
		clamd.script(FakeClamd.Script.OK);

		assertEquals(ScanVerdict.CLEAN, scanner.scan(sample("a clean file")));
		assertEquals("a clean file", new String(clamd.streams().get(0)));
	}

	@Test
	void largeStream_isSentInChunks() {
		clamd.script(FakeClamd.Script.OK);
		byte[] body = new byte[3 * 1024 * 1024 + 17];

		assertEquals(ScanVerdict.CLEAN, scanner.scan(new ByteArrayInputStream(body)));
		assertEquals(body.length, clamd.streams().get(0).length);
	}

	@Test
	void limitExceeded_isPerFileCondition() {
		clamd.script(FakeClamd.Script.LIMIT);

		ScanVerdict verdict = scanner.scan(sample("big"));

		assertEquals(ScanVerdict.Kind.PER_FILE_CONDITION, verdict.kind());
		assertEquals("SCAN_LIMIT", verdict.conditionCodeOrNull());
	}

	@Test
	void otherErrorLine_isPerFileCondition() {
		clamd.script(FakeClamd.Script.ERROR_LINE);

		ScanVerdict verdict = scanner.scan(sample("big"));

		assertEquals(ScanVerdict.Kind.PER_FILE_CONDITION, verdict.kind());
		assertEquals("SCAN_ERROR", verdict.conditionCodeOrNull());
	}

	@Test
	void refusedResetOrTimeout_isSilence() throws IOException {
		clamd.script(FakeClamd.Script.RESET);
		assertThrows(ScannerSilentException.class, () -> scanner.scan(sample("x")));

		clamd.script(FakeClamd.Script.NO_ANSWER);
		long start = System.nanoTime();
		assertThrows(ScannerSilentException.class, () -> scanner.scan(sample("x")));
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(6));

		assertThrows(ScannerSilentException.class, () -> new ClamAvScanner("127.0.0.1", closedPort(), null,
				Duration.ofSeconds(1)).scan(sample("x")));
	}

	@Test
	void version_parsesSignatureDate() {
		clamd.version("ClamAV 1.4.1/27450/Mon Oct  3 08:24:01 2026");

		assertEquals(Instant.parse("2026-10-03T08:24:01Z"), scanner.signatureDate());
	}

	@Test
	void probe_wellBehaved_passes() {
		clamd.script(FakeClamd.Script.OK_THEN_FOUND_FOR_EICAR);

		assertEquals(ProbeResult.PASSED, scanner.probe(NOW));
		assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains("Eicar"));
	}

	@Test
	void probe_eicarNotFound_isWrongAnswer() {
		clamd.script(FakeClamd.Script.OK);

		assertEquals(ProbeResult.WRONG_ANSWER, scanner.probe(NOW));
	}

	@Test
	void probe_eicarFoundOnlyAsLimit_isWrongAnswer() {
		clamd.script(FakeClamd.Script.OK_THEN_LIMIT_FOR_EICAR);

		assertEquals(ProbeResult.WRONG_ANSWER, scanner.probe(NOW));
	}

	@Test
	void probe_cleanSampleNotOk_isWrongAnswer() {
		clamd.script(FakeClamd.Script.FOUND);

		assertEquals(ProbeResult.WRONG_ANSWER, scanner.probe(NOW));
	}

	@Test
	void probe_staleSignatures_isWrongAnswer() {
		clamd.script(FakeClamd.Script.OK_THEN_FOUND_FOR_EICAR).version("ClamAV 1.4.1/27450/Mon Oct 27 08:24:01 2026");

		assertEquals(ProbeResult.WRONG_ANSWER, scanner.probe(NOW));
	}

	@Test
	void probe_pingWithoutAnswer_isIncomplete() {
		clamd.script(FakeClamd.Script.OK_THEN_FOUND_FOR_EICAR).refusePing(true);

		assertEquals(ProbeResult.INCOMPLETE, scanner.probe(NOW));
	}

	@Test
	void probe_noConnection_isIncomplete() throws IOException {
		assertEquals(ProbeResult.INCOMPLETE, new ClamAvScanner("127.0.0.1", closedPort(), null, Duration.ofSeconds(1))
				.probe(NOW));
	}

	private static ByteArrayInputStream sample(String text) {
		return new ByteArrayInputStream(text.getBytes());
	}

	private static int closedPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
