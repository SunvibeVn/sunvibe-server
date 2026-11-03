package vn.sunvibe.survey.media;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;

/**
 * clamd must answer a slow scan before the client gives up (survey-media spec A-7, A-27; security review I-1): a scan
 * that reaches clamd's MaxScanTime is answered "Heuristics.Limits.Exceeded.MaxScanTime FOUND", a per-file condition
 * counted as an expensive outcome and rejected after two retries. If the client's read timeout came first, the same file
 * would count as clamd being down (all six attempts, no expensive count, a probe request each time, and a busy clamd
 * thread after the hang-up). The client timeout keeps at least 10 seconds over MaxScanTime for the queue and transfer.
 */
class ClamdTimeoutRelationTest {

	private static final Path CLAMD_CONF = Path.of("..", "docker", "clamav", "clamd.conf");

	@Test
	void clientTimeout_exceedsClamdMaxScanTimeWithMargin() throws Exception {
		Matcher matcher = Pattern.compile("(?m)^MaxScanTime\\s+(\\d+)\\s*$").matcher(Files.readString(CLAMD_CONF));
		assertTrue(matcher.find(), "clamd.conf sets MaxScanTime");
		Duration maxScanTime = Duration.ofMillis(Long.parseLong(matcher.group(1)));
		Duration clientTimeout = new MediaProperties().getClamav().getTimeout();

		assertTrue(clientTimeout.compareTo(maxScanTime.plusSeconds(10)) >= 0,
				"client timeout " + clientTimeout + " must exceed MaxScanTime " + maxScanTime + " by 10 s");
	}
}
