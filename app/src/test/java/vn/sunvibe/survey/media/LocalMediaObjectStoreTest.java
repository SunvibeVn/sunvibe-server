package vn.sunvibe.survey.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.PutOutcome;
import vn.sunvibe.survey.core.infrastructure.media.store.LocalMediaObjectStore;

/** The store contract on a local directory (survey-media spec A-1: dev, test and lan only). */
class LocalMediaObjectStoreTest extends MediaObjectStoreContract {

	@TempDir
	Path root;

	@Override
	protected MediaObjectStore store() {
		return new LocalMediaObjectStore(root);
	}

	@Override
	protected MediaObjectStore brokenStore() {
		// A root that is a file: no directory can be created under it
		Path file = root.resolve("not-a-directory");
		try {
			java.nio.file.Files.writeString(file, "x");
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		return new LocalMediaObjectStore(file);
	}

	/**
	 * survey-media spec I-3, SV-068: a create-only put never replaces a key, however the writers interleave. On Linux a
	 * rename replaces its target, so an "exists" check followed by a rename lets two writers both create.
	 */
	@Test
	void concurrentPutsSameKey_manyRounds_exactlyOneCreatedAndItsBytesKept() throws Exception {
		MediaObjectStore store = store();
		int writers = 8;
		ExecutorService pool = Executors.newFixedThreadPool(writers);
		try {
			for (int round = 0; round < 50; round++) {
				String key = "media/round-" + round + "/1/FULL";
				CyclicBarrier start = new CyclicBarrier(writers);
				List<Future<PutOutcome>> outcomes = new ArrayList<>();
				for (int i = 0; i < writers; i++) {
					byte[] body = ("writer-" + i).getBytes(StandardCharsets.UTF_8);
					outcomes.add(pool.submit((Callable<PutOutcome>) () -> {
						start.await(10, TimeUnit.SECONDS);
						return store.putCreateOnly(Bucket.MEDIA, key, new ByteArrayInputStream(body), body.length);
					}));
				}
				List<String> created = new ArrayList<>();
				for (int i = 0; i < writers; i++) {
					if (outcomes.get(i).get(30, TimeUnit.SECONDS) == PutOutcome.CREATED) {
						created.add("writer-" + i);
					}
				}

				assertEquals(1, created.size(), "round " + round + ": " + created);
				try (MediaObjectStore.StoredObject stored = store.get(Bucket.MEDIA, key)) {
					assertEquals(created.get(0), new String(stored.content().readAllBytes(), StandardCharsets.UTF_8),
							"round " + round + ": the bytes of the writer that created the key");
				}
			}
			try (var files = Files.walk(root)) {
				assertTrue(files.noneMatch(file -> file.getFileName().toString().startsWith(".tmp-")),
						"no temporary file is left");
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
