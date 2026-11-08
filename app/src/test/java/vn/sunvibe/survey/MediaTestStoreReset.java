package vn.sunvibe.survey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Empties the folder of the media test store and spool ({@link MediaApiTestBase}) once per test JVM, before any Spring
 * context starts (SV-264). Nothing else cleans that folder, and a sweep walks all of it: at 8,624 files left by earlier
 * runs, MediaIntakePausePostgresTest ran 1019 s and outlived its 15-minute token.
 *
 * <p>Registered through {@code META-INF/services}, so it also covers the HTTP-stack tests that use the folder without
 * extending {@link MediaApiTestBase}. It acts only when the {@code media.test.reset} system property is set, which
 * {@code app/build.gradle.kts} does for the {@code test} task alone: {@code exportOpenApi} and the other test tasks share
 * this classpath and must not wipe the store under a running build. It runs at the start of a run, not the end, so the
 * files of a failed run stay to read.
 */
public final class MediaTestStoreReset implements LauncherSessionListener {

	static final String SWITCH = "media.test.reset";

	/** For {@code ServiceLoader}. */
	public MediaTestStoreReset() {
	}

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		if (Boolean.getBoolean(SWITCH)) {
			clear(Path.of(System.getProperty("java.io.tmpdir"), "sunvibe-media-api-test"));
		}
	}

	/** Deletes everything under {@code root} and keeps the folder; a failed delete fails the run, never silently. */
	static void clear(Path root) {
		if (!Files.isDirectory(root)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			List<Path> deepestFirst = paths.filter(path -> !path.equals(root)).sorted(Comparator.reverseOrder()).toList();
			for (Path path : deepestFirst) {
				Files.delete(path);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Could not empty the media test store at " + root, e);
		}
	}
}
