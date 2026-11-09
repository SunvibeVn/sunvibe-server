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
	/**
	 * The folder that holds the store, set by {@code app/build.gradle.kts} to the build folder of the run (SV-271): two
	 * checkouts then never empty each other's files. {@link MediaApiTestBase} reads the same property.
	 */
	static final String DIR = "media.test.dir";
	/** The last segment of every store path; MediaContentReadApiPostgresTest checks it never leaks. */
	static final String SEGMENT = "sunvibe-media-api-test";

	/** For {@code ServiceLoader}. */
	public MediaTestStoreReset() {
	}

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		if (Boolean.getBoolean(SWITCH)) {
			clear(root());
		}
	}

	/** The store and spool folder of this test JVM. */
	public static Path root() {
		return root(System.getProperty(DIR), System.getProperty("java.io.tmpdir"));
	}

	/** Under {@code dir} when the build gives one, else under the temp folder, as before SV-271 (a run outside Gradle). */
	static Path root(String dir, String tmpdir) {
		return Path.of(dir != null ? dir : tmpdir, SEGMENT);
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
