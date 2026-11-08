package vn.sunvibe.survey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The media test store starts each test run empty (SV-264); here on a folder of the test's own. */
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

	@Test
	void clear_noFolder_doesNothing() {
		Path root = temp.resolve("absent");

		MediaTestStoreReset.clear(root);

		assertFalse(Files.exists(root));
	}
}
