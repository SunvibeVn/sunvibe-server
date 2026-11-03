package vn.sunvibe.survey.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer.NormalizeResult;
import vn.sunvibe.survey.core.application.shared.external.media.ProcessorUnavailableException;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.infrastructure.media.processor.IsolatedProcessorClient;

/**
 * The processor client against a scripted processor (survey-media spec 4.5, A-28; SM-33): each variant read within
 * its cap and the whole reply within 15 MiB, the client stopping at a cap; the SOF and bounds checks in Java; the
 * processor version from configuration only; a crash or a dead channel is unavailable.
 */
class IsolatedProcessorClientTest {

	private FakeProcessor processor;
	private IsolatedProcessorClient client;

	@BeforeEach
	void start() throws IOException {
		processor = new FakeProcessor();
		client = new IsolatedProcessorClient("127.0.0.1", processor.port(), "dev-8.16.1", Duration.ofSeconds(3),
				Duration.ofSeconds(10));
	}

	@AfterEach
	void stop() throws IOException {
		processor.close();
	}

	@Test
	void valid_threeCheckedVariants() {
		NormalizeResult result = normalize();

		NormalizeResult.Normalized normalized = assertInstanceOf(NormalizeResult.Normalized.class, result);
		assertEquals(1200, normalized.sourceWidth());
		assertEquals(3, normalized.variants().size());
		assertEquals(VariantKind.THUMBNAIL, normalized.variants().get(2).kind());
		assertEquals(400, normalized.variants().get(2).width());
		assertThat(normalized.variants().get(0).toString()).doesNotContain("[B@");
	}

	@Test
	void fullOver12MiB_failsAndStopsAtCap() {
		processor.script(FakeProcessor.Script.FULL_OVER_CAP);

		assertEquals(new NormalizeResult.Failed("VARIANT_OVER_CAP"), normalize());
	}

	@Test
	void previewOver2MiB_fails() {
		processor.script(FakeProcessor.Script.PREVIEW_OVER_CAP);

		assertEquals(new NormalizeResult.Failed("VARIANT_OVER_CAP"), normalize());
	}

	@Test
	void thumbnailOver256KiB_fails() {
		processor.script(FakeProcessor.Script.THUMBNAIL_OVER_CAP);

		assertEquals(new NormalizeResult.Failed("VARIANT_OVER_CAP"), normalize());
	}

	@Test
	void responseOver15MiB_fails() {
		processor.script(FakeProcessor.Script.RESPONSE_OVER_CAP);

		assertEquals(new NormalizeResult.Failed("RESPONSE_OVER_CAP"), normalize());
	}

	@Test
	void fullSof5000px_fails() {
		processor.script(FakeProcessor.Script.FULL_SOF_5000);

		assertEquals(new NormalizeResult.Failed("VARIANT_BOUNDS"), normalize());
	}

	@Test
	void rejectedAndLimitHit_areForwarded() {
		processor.script(FakeProcessor.Script.REJECTED_ANIMATED);
		assertEquals(new NormalizeResult.Rejected(MediaStatusReason.ANIMATED), normalize());

		processor.script(FakeProcessor.Script.LIMIT_HIT);
		assertEquals(new NormalizeResult.LimitHit(), normalize());
	}

	@Test
	void crashOrDeadChannel_isUnavailable() throws IOException {
		processor.script(FakeProcessor.Script.CRASH);
		assertThrows(ProcessorUnavailableException.class, this::normalize);

		IsolatedProcessorClient dead = new IsolatedProcessorClient("127.0.0.1", closedPort(), "dev-8.16.1",
				Duration.ofSeconds(1), Duration.ofSeconds(1));
		assertThrows(ProcessorUnavailableException.class, () -> dead.normalize(new ByteArrayInputStream(new byte[1]),
				1));
	}

	/** survey-media SV-069 (security, with I-1): a processor that stops reading the source ends the call by the timeout. */
	@Test
	void peerStopsReading_normalizeUnavailableWithinTimeout() throws Exception {
		try (ClamAvScannerTest.NonReadingPeer stalled = new ClamAvScannerTest.NonReadingPeer()) {
			IsolatedProcessorClient stalledClient = new IsolatedProcessorClient("127.0.0.1", stalled.port(),
					"dev-8.16.1", Duration.ofSeconds(1), Duration.ofSeconds(1));

			assertTimeoutPreemptively(Duration.ofSeconds(20), () -> assertThrows(ProcessorUnavailableException.class,
					() -> stalledClient.normalize(ClamAvScannerTest.endless(20L * 1024 * 1024), 20L * 1024 * 1024)));
		}
	}

	@Test
	void detect_answersTheFormat() {
		assertEquals(SourceFormat.JPEG, client.detect(new ByteArrayInputStream(new byte[4]), 4).orElseThrow());
	}

	@Test
	void processorVersion_fromConfiguration() {
		assertEquals("dev-8.16.1", client.processorVersion());
	}

	@Test
	void probe_validOutputPasses_slowIsIncomplete_badOutputWrong() {
		assertEquals(ProbeResult.PASSED, client.probe());

		processor.script(FakeProcessor.Script.FULL_SOF_5000);
		assertEquals(ProbeResult.WRONG_ANSWER, client.probe());

		processor.script(FakeProcessor.Script.SLOW);
		assertEquals(ProbeResult.INCOMPLETE, new IsolatedProcessorClient("127.0.0.1", processor.port(), "dev-8.16.1",
				Duration.ofSeconds(1), Duration.ofSeconds(1)).probe());
	}

	private NormalizeResult normalize() {
		return client.normalize(new ByteArrayInputStream(new byte[16]), 16);
	}

	private static int closedPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
