package vn.sunvibe.survey.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer.NormalizeResult;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer.NormalizedVariant;
import vn.sunvibe.survey.core.domain.model.media.MediaStatusReason;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.domain.model.media.VariantKind;
import vn.sunvibe.survey.core.infrastructure.media.processor.IsolatedProcessorClient;

/**
 * The processing container on the synthetic corpus of app/src/test/resources/media/fixtures (survey-media spec 4.5,
 * A-3, A-4; SM-1, SM-2; plan PD-6): stills of each format become three clean JPEG variants, oriented and resized down
 * only; animations, motion photos, composites, truncated files, huge headers and unsupported formats get their
 * reasons. Needs Docker; the image is the one ProcessorIsolationProbeTest builds.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessorCorpusTest {

	private GenericContainer<?> container;
	private IsolatedProcessorClient client;

	@BeforeAll
	void start() {
		container = ProcessorImage.hardened(List.of("--listen", "tcp:0.0.0.0:" + ProcessorImage.PORT))
				.withExposedPorts(ProcessorImage.PORT)
				.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(15)));
		container.start();
		client = new IsolatedProcessorClient(container.getHost(), container.getMappedPort(ProcessorImage.PORT),
				"dev-8.16.1", Duration.ofSeconds(30), Duration.ofSeconds(130));
	}

	@AfterAll
	void stop() {
		container.stop();
	}

	// ==================== SM-1 ====================

	@Test
	void jpegPngWebpHeic_becomeThreeJpegVariants() throws IOException {
		for (String file : List.of("still.jpg", "small-alpha.png", "still.webp", "still.heic")) {
			NormalizeResult.Normalized normalized = normalized(file);
			assertEquals(List.of(VariantKind.FULL, VariantKind.PREVIEW, VariantKind.THUMBNAIL),
					normalized.variants().stream().map(NormalizedVariant::kind).toList(), file);
		}
		assertEquals(Optional.of(SourceFormat.HEIF), client.detect(stream("still.heic"), size("still.heic")));
		assertEquals(Optional.of(SourceFormat.WEBP), client.detect(stream("still.webp"), size("still.webp")));
		assertEquals(Optional.of(SourceFormat.PNG), client.detect(stream("small-alpha.png"), size("small-alpha.png")));
	}

	@Test
	void outputs_carryNoExifGpsXmpIptcIccCommentAuxiliaryOrThumbnail() throws IOException {
		for (String file : List.of("still.jpg", "still.webp", "still.heic", "small-alpha.png")) {
			for (NormalizedVariant variant : normalized(file).variants()) {
				String text = new String(variant.jpeg(), StandardCharsets.ISO_8859_1);
				for (String forbidden : List.of("Exif", "http://ns.adobe.com/xap", "ICC_PROFILE", "Photoshop",
						"secret", "SunvibeTestCam", "MPF", "JFXX")) {
					assertThat(text).as(file + " " + variant.kind()).doesNotContain(forbidden);
				}
			}
		}
	}

	@Test
	void rotatedJpeg_isAutoOriented() throws IOException {
		NormalizedVariant full = normalized("rotated.jpg").variants().get(0);

		assertEquals(4096, full.height());
		assertTrue(full.width() < full.height(), full.width() + "x" + full.height());
	}

	@Test
	void largeVariantsResizedDownOnly() throws IOException {
		List<NormalizedVariant> variants = normalized("small-alpha.png").variants();

		assertEquals(800, variants.get(0).width());
		assertEquals(600, variants.get(0).height());
		assertEquals(800, variants.get(1).width());
		assertEquals(400, variants.get(2).width());
		assertEquals(300, variants.get(2).height());
	}

	// ==================== SM-2 ====================

	@Test
	void svgPdfGifAvifRenamedText_unsupported() throws IOException {
		for (String file : List.of("image.svg", "document.pdf", "still.gif", "image.avif", "notes.jpg")) {
			assertEquals(Optional.empty(), client.detect(stream(file), size(file)), file);
			assertEquals(new NormalizeResult.Rejected(MediaStatusReason.UNSUPPORTED_FORMAT), normalize(file), file);
		}
	}

	@Test
	void animatedWebpAndApng_animated() throws IOException {
		assertEquals(new NormalizeResult.Rejected(MediaStatusReason.ANIMATED), normalize("animated.webp"));
		assertEquals(new NormalizeResult.Rejected(MediaStatusReason.ANIMATED), normalize("animated.png"));
	}

	@Test
	void jpegWithMp4AndHeifWithVideo_motionPhoto() throws IOException {
		assertEquals(new NormalizeResult.Rejected(MediaStatusReason.MOTION_PHOTO), normalize("motion.jpg"));
		assertEquals(new NormalizeResult.Rejected(MediaStatusReason.MOTION_PHOTO), normalize("heif-video.heic"));
	}

	@Test
	void jpegWithZip_compositeFile() throws IOException {
		assertEquals(new NormalizeResult.Rejected(MediaStatusReason.COMPOSITE_FILE), normalize("composite.jpg"));
	}

	@Test
	void truncatedJpeg_unreadable() throws IOException {
		assertEquals(new NormalizeResult.Rejected(MediaStatusReason.UNREADABLE), normalize("truncated.jpg"));
	}

	@Test
	void hugePixelPng_tooManyPixels() throws IOException {
		assertEquals(new NormalizeResult.Rejected(MediaStatusReason.TOO_MANY_PIXELS), normalize("huge-pixels.png"));
	}

	@Test
	void unsupportedHeicVariant_unsupportedOrUnreadableNeverMalware() throws IOException {
		NormalizeResult result = normalize("av1-primary.heif");

		assertThat(result).isIn(new NormalizeResult.Rejected(MediaStatusReason.UNSUPPORTED_FORMAT),
				new NormalizeResult.Rejected(MediaStatusReason.UNREADABLE));
	}

	private NormalizeResult.Normalized normalized(String file) throws IOException {
		return assertInstanceOf(NormalizeResult.Normalized.class, normalize(file), file);
	}

	private NormalizeResult normalize(String file) throws IOException {
		return client.normalize(stream(file), size(file));
	}

	private static InputStream stream(String file) throws IOException {
		return new ByteArrayInputStream(bytes(file));
	}

	private static long size(String file) throws IOException {
		return bytes(file).length;
	}

	static byte[] bytes(String file) throws IOException {
		try (InputStream in = ProcessorCorpusTest.class.getResourceAsStream("/media/fixtures/" + file)) {
			if (in == null) {
				throw new IllegalStateException("missing fixture " + file + ": run scripts/dev/make-media-fixtures.sh");
			}
			return in.readAllBytes();
		}
	}
}
