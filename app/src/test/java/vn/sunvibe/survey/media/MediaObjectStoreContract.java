package vn.sunvibe.survey.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.github.f4b6a3.ulid.UlidCreator;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.Bucket;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ListedObject;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.ObjectPage;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.PutOutcome;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore.StoredObject;
import vn.sunvibe.survey.core.application.shared.external.media.StoreObjectNotFoundException;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;

/**
 * The contract every media object store meets (survey-media spec 4.12, I-3, A-15; SM-18): create-only puts (a second
 * put of a key answers EXISTS, concurrent puts create one), reads give back the exact bytes, deletes leave an empty
 * listing, listings page in key order after a start key, probes work under the health prefixes, and failures never
 * carry a key or bucket. Run on a local directory and MinIO in the build, and on an R2 test bucket before go-live.
 */
public abstract class MediaObjectStoreContract {

	/** The store under test, configured with this contract's two buckets. */
	protected abstract MediaObjectStore store();

	/** A store whose backend refuses or cannot be reached, to check the failure messages. */
	protected abstract MediaObjectStore brokenStore();

	/** The connections of the store's serving pool; 0 for a store without one. */
	protected int servingConnections() {
		return 0;
	}

	/** A prefix of its own per test, so runs on a shared bucket never meet. */
	protected String prefix() {
		return "media/" + UlidCreator.getMonotonicUlid() + "/";
	}

	@Test
	void putCreateOnly_secondPutSameKey_answersExists() throws IOException {
		String key = prefix() + "1/FULL";

		assertEquals(PutOutcome.CREATED, put(Bucket.MEDIA, key, "first"));
		assertEquals(PutOutcome.EXISTS, put(Bucket.MEDIA, key, "second"));

		assertArrayEquals(bytes("first"), read(Bucket.MEDIA, key));
		store().delete(Bucket.MEDIA, key);
	}

	@Test
	void concurrentPutsSameKey_oneCreated() throws Exception {
		String key = prefix() + "1/PREVIEW";
		ExecutorService pool = Executors.newFixedThreadPool(6);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<PutOutcome>> outcomes = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				String body = "writer-" + i;
				outcomes.add(pool.submit((Callable<PutOutcome>) () -> {
					start.await(10, TimeUnit.SECONDS);
					return put(Bucket.MEDIA, key, body);
				}));
			}
			start.countDown();
			int created = 0;
			for (Future<PutOutcome> outcome : outcomes) {
				if (outcome.get(30, TimeUnit.SECONDS) == PutOutcome.CREATED) {
					created++;
				}
			}
			assertEquals(1, created);
			assertTrue(new String(read(Bucket.MEDIA, key), StandardCharsets.UTF_8).startsWith("writer-"));
		} finally {
			pool.shutdownNow();
			store().delete(Bucket.MEDIA, key);
		}
	}

	@Test
	void get_returnsBytesAndSha() throws Exception {
		String key = "quarantine/" + UlidCreator.getMonotonicUlid() + "/1";
		byte[] body = new byte[300_000];
		for (int i = 0; i < body.length; i++) {
			body[i] = (byte) (i * 31);
		}

		store().putCreateOnly(Bucket.QUARANTINE, key, new ByteArrayInputStream(body), body.length);
		byte[] background = read(Bucket.QUARANTINE, key);

		assertEquals(sha256(body), sha256(background));
		String mediaKey = prefix() + "1/THUMBNAIL";
		store().putCreateOnly(Bucket.MEDIA, mediaKey, new ByteArrayInputStream(body), body.length);
		try (StoredObject served = store().getForServing(mediaKey)) {
			assertEquals(body.length, served.length());
			assertEquals(sha256(body), sha256(served.content().readAllBytes()));
		}
		store().delete(Bucket.QUARANTINE, key);
		store().delete(Bucket.MEDIA, mediaKey);
	}

	/**
	 * spec A-25 (SV-069): the read watchdog ends a stalled read with abort(), which returns at once without reading the
	 * rest of a 12 MiB FULL and frees its serving connection: more aborted reads than the pool has connections leave a
	 * whole read possible, and the aborted stream gives no more bytes.
	 */
	@Test
	void abort_returnsAtOnceWithoutReadingTheRest_servingConnectionFreed() throws Exception {
		String key = prefix() + "1/FULL";
		byte[] body = new byte[12 * 1024 * 1024];
		for (int i = 0; i < body.length; i++) {
			body[i] = (byte) (i * 17);
		}
		store().putCreateOnly(Bucket.MEDIA, key, new ByteArrayInputStream(body), body.length);
		try {
			for (int round = 0; round <= servingConnections(); round++) {
				StoredObject served = store().getForServing(key);
				assertTrue(served.content().read(new byte[16 * 1024]) > 0);

				long start = System.nanoTime();
				served.abort().run();
				long abortMillis = java.time.Duration.ofNanos(System.nanoTime() - start).toMillis();

				assertTrue(abortMillis < 500, "abort returned at once: " + abortMillis + " ms");
				assertThrows(IOException.class, () -> served.content().readAllBytes(), "no more bytes after abort");
				served.close();
			}
			try (StoredObject whole = store().getForServing(key)) {
				assertEquals(sha256(body), sha256(whole.content().readAllBytes()), "the connections were freed");
			}
		} finally {
			store().delete(Bucket.MEDIA, key);
		}
	}

	@Test
	void get_missingKey_isNotFound() {
		assertThrows(StoreObjectNotFoundException.class,
				() -> store().get(Bucket.MEDIA, prefix() + "1/FULL").close());
		assertThrows(StoreObjectNotFoundException.class,
				() -> store().getForServing(prefix() + "1/FULL").close());
	}

	@Test
	void delete_thenListPrefix_isEmpty() throws IOException {
		String prefix = prefix();
		for (String variant : List.of("FULL", "PREVIEW", "THUMBNAIL")) {
			put(Bucket.MEDIA, prefix + "1/" + variant, variant);
		}
		assertEquals(3, store().list(Bucket.MEDIA, prefix, null, 1000).objects().size());

		for (String variant : List.of("FULL", "PREVIEW", "THUMBNAIL")) {
			store().delete(Bucket.MEDIA, prefix + "1/" + variant);
		}
		store().delete(Bucket.MEDIA, prefix + "1/NEVER-WRITTEN");

		assertTrue(store().list(Bucket.MEDIA, prefix, null, 1000).objects().isEmpty());
	}

	@Test
	void list_startAfter_pagesInKeyOrder() throws IOException {
		String prefix = prefix();
		List<String> keys = List.of(prefix + "1/A", prefix + "1/B", prefix + "2/A", prefix + "2/B", prefix + "3/A");
		for (String key : keys.reversed()) {
			put(Bucket.MEDIA, key, key);
		}

		ObjectPage first = store().list(Bucket.MEDIA, prefix, null, 2);
		ObjectPage second = store().list(Bucket.MEDIA, prefix, first.objects().get(1).key(), 2);
		ObjectPage third = store().list(Bucket.MEDIA, prefix, second.objects().get(1).key(), 2);

		assertEquals(keys.subList(0, 2), first.objects().stream().map(ListedObject::key).toList());
		assertTrue(first.truncated());
		assertEquals(keys.subList(2, 4), second.objects().stream().map(ListedObject::key).toList());
		assertEquals(keys.subList(4, 5), third.objects().stream().map(ListedObject::key).toList());
		assertFalse(third.truncated());
		assertTrue(first.objects().get(0).lastModified() != null);
		for (String key : keys) {
			store().delete(Bucket.MEDIA, key);
		}
	}

	@Test
	void probe_writesGetsDeletesUnderHealthPrefix() {
		store().probe(Bucket.QUARANTINE);
		store().probe(Bucket.MEDIA);

		assertTrue(store().list(Bucket.QUARANTINE, "quarantine/health/", null, 1000).objects().isEmpty());
		assertTrue(store().list(Bucket.MEDIA, "health/", null, 1000).objects().isEmpty());
	}

	@Test
	void errors_neverCarryKeyOrBucketInMessage() {
		String key = prefix() + "1/FULL";
		List<RuntimeException> failures = new ArrayList<>();
		failures.add(assertThrows(StoreUnavailableException.class,
				() -> brokenStore().putCreateOnly(Bucket.MEDIA, key, new ByteArrayInputStream(bytes("x")), 1)));
		failures.add(assertThrows(StoreUnavailableException.class, () -> brokenStore().get(Bucket.MEDIA, key)));
		failures.add(assertThrows(StoreUnavailableException.class, () -> brokenStore().getForServing(key)));
		failures.add(assertThrows(StoreUnavailableException.class, () -> brokenStore().probe(Bucket.MEDIA)));

		for (RuntimeException failure : failures) {
			for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
				String message = String.valueOf(cause.getMessage());
				assertFalse(message.contains(key), message);
				assertFalse(message.contains("media/"), message);
				assertFalse(message.contains("bucket"), message);
			}
			assertTrue(((StoreUnavailableException) failure).code().startsWith("STORE_"));
		}
	}

	protected PutOutcome put(Bucket bucket, String key, String text) {
		byte[] body = bytes(text);
		return store().putCreateOnly(bucket, key, new ByteArrayInputStream(body), body.length);
	}

	protected byte[] read(Bucket bucket, String key) throws IOException {
		try (StoredObject object = store().get(bucket, key); InputStream in = object.content()) {
			return in.readAllBytes();
		}
	}

	protected static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	protected static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
