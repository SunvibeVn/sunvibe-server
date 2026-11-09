package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The media test store starts each test run empty (SV-264), in a folder of the build that runs (SV-271); here on a folder
 * of the test's own.
 */
class MediaTestStoreResetTest {

	@TempDir
	Path temp;

	@Test
	void clear_removesEverythingUnderTheRoot() throws Exception {
		Path root = temp.resolve("sunvibe-media-api-test");
		Files.createDirectories(root.resolve("store/media/ab"));
		Files.writeString(root.resolve("store/media/ab/object.bin"), "photo");
		Files.createDirectories(root.resolve("spool"));
		Files.writeString(root.resolve("spool/upload.part"), "part");
		Files.writeString(root.resolve("marker"), "left by an earlier run");

		MediaTestStoreReset.clear(root);

		try (Stream<Path> left = Files.list(root)) {
			assertEquals(List.of(), left.toList());
		}
	}

	/** SV-271: two checkouts, two build folders, two stores; a run empties only its own. */
	@Test
	void root_buildFolderGiven_isUnderIt() {
		Path build = temp.resolve("checkout-a/app/build");

		assertEquals(build.resolve("sunvibe-media-api-test"),
				MediaTestStoreReset.root(build.toString(), temp.resolve("tmp").toString()));
	}

	/** A run outside Gradle (an IDE) has no build folder property and keeps the temp folder of before. */
	@Test
	void root_noBuildFolder_isTheTempFolder() {
		assertEquals(temp.resolve("tmp").resolve("sunvibe-media-api-test"),
				MediaTestStoreReset.root(null, temp.resolve("tmp").toString()));
	}

	/** MediaContentReadApiPostgresTest looks for this segment in answers and logs: every root keeps it. */
	@Test
	void root_alwaysEndsWithTheSegmentTheLeakCheckLooksFor() {
		assertEquals("sunvibe-media-api-test", MediaTestStoreReset.root(null, temp.toString()).getFileName().toString());
		assertEquals("sunvibe-media-api-test",
				MediaTestStoreReset.root(temp.resolve("b").toString(), temp.toString()).getFileName().toString());
	}

	@Test
	void clear_noFolder_doesNothing() {
		Path root = temp.resolve("absent");

		MediaTestStoreReset.clear(root);

		assertFalse(Files.exists(root));
	}
}
