package vn.sunvibe.survey.media;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer.NormalizeResult;
import vn.sunvibe.survey.core.infrastructure.media.processor.IsolatedProcessorClient;

/**
 * The owner's real phone photos (survey-media spec SM-1, A-14; plan PD-6; SV-042): every file of the directory in
 * SUNVIBE_PHONE_CORPUS_DIR (iPhone HEIC with auxiliary images and a Live Photo still, a Samsung JPEG with its vendor
 * trailer, Pixel Ultra HDR, 50, 64 and 108 MP modes, a large rotated JPEG) becomes three checked variants. The photos
 * stay outside git. Tagged: {@code ./gradlew :app:phoneCorpusTest}; it fails without the directory.
 */
@Tag("phone-corpus")
class PhoneCorpusTest {

	@Test
	void everyCorpusFile_becomesReadyWithinLimits() throws IOException {
		String directory = System.getenv("SUNVIBE_PHONE_CORPUS_DIR");
		if (directory == null || directory.isBlank() || !Files.isDirectory(Path.of(directory))) {
			throw new IllegalStateException("phoneCorpusTest needs SUNVIBE_PHONE_CORPUS_DIR: a directory of real phone"
					+ " photos taken for testing (plan PD-6)");
		}
		List<Path> files;
		try (Stream<Path> listing = Files.list(Path.of(directory))) {
			files = listing.filter(Files::isRegularFile).sorted().toList();
		}
		assertTrue(!files.isEmpty(), "the corpus directory is empty");
		try (GenericContainer<?> container = ProcessorImage.hardened(List.of("--listen",
						"tcp:0.0.0.0:" + ProcessorImage.PORT))
				.withExposedPorts(ProcessorImage.PORT)
				.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(15)))) {
			container.start();
			IsolatedProcessorClient client = new IsolatedProcessorClient(container.getHost(),
					container.getMappedPort(ProcessorImage.PORT), "phone-corpus", Duration.ofSeconds(30),
					Duration.ofSeconds(130));
			for (Path file : files) {
				byte[] bytes = Files.readAllBytes(file);
				NormalizeResult result = client.normalize(new java.io.ByteArrayInputStream(bytes), bytes.length);
				assertInstanceOf(NormalizeResult.Normalized.class, result, file.getFileName() + ": " + result);
			}
		}
	}
}
