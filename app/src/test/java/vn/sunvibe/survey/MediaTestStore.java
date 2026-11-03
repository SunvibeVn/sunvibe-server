package vn.sunvibe.survey;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;

/**
 * The media tests' store: the configured one behind a test-only spy (plan Global Constraints: switches vary at run
 * time). It counts the serving reads, can make them fail like a store without a free GET connection or slow them
 * down, and records whether and when each served stream was closed (the R2 stream released, spec A-25).
 */
public final class MediaTestStore implements MediaObjectStore {

	private final MediaObjectStore real;
	public final AtomicInteger servingReads = new AtomicInteger();
	public final AtomicBoolean failServing = new AtomicBoolean();
	public final AtomicInteger openServingStreams = new AtomicInteger();
	/** Deletes do nothing while set, like a store whose listing still shows the objects (spec 4.7). */
	public final AtomicBoolean skipDeletes = new AtomicBoolean();
	/** Deletes fail like a store that does not answer. */
	public final AtomicBoolean failDeletes = new AtomicBoolean();
	/** Probes fail like a store that does not answer (spec A-18). */
	public final AtomicBoolean failProbe = new AtomicBoolean();
	/** Listings of the media bucket answer nothing, like an empty or wrong bucket after a restore. */
	public final AtomicBoolean emptyMediaBucket = new AtomicBoolean();
	/** Listings of one photo's prefix answer nothing while the bucket listing still answers. */
	public final AtomicBoolean emptyPhotoPrefixes = new AtomicBoolean();
	/** Serving streams give at most 64 KB per 200 ms, like a slow store stream that still makes progress. */
	public final AtomicBoolean slowServing = new AtomicBoolean();
	/** The streams served since the last {@link #reset()}, in order; a stream of an earlier test is never in it. */
	public final List<ServedStream> served = new CopyOnWriteArrayList<>();
	public volatile boolean lastServedClosed;

	public MediaTestStore(MediaObjectStore real) {
		this.real = real;
	}

	public void reset() {
		servingReads.set(0);
		failServing.set(false);
		skipDeletes.set(false);
		failDeletes.set(false);
		failProbe.set(false);
		emptyMediaBucket.set(false);
		emptyPhotoPrefixes.set(false);
		slowServing.set(false);
		served.clear();
	}

	/** One served stream: whether and when ({@link System#nanoTime()}) it was closed. */
	public static final class ServedStream {

		private boolean closed;
		private long closedAtNanos;

		/** True for the first close only. */
		synchronized boolean close() {
			if (closed) {
				return false;
			}
			closed = true;
			closedAtNanos = System.nanoTime();
			return true;
		}

		public synchronized boolean closed() {
			return closed;
		}

		public synchronized long closedAtNanos() {
			return closedAtNanos;
		}
	}

	@Override
	public PutOutcome putCreateOnly(Bucket bucket, String key, InputStream content, long length) {
		return real.putCreateOnly(bucket, key, content, length);
	}

	@Override
	public StoredObject get(Bucket bucket, String key) {
		return real.get(bucket, key);
	}

	@Override
	public StoredObject getForServing(String key) {
		servingReads.incrementAndGet();
		if (failServing.get()) {
			throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
		}
		StoredObject object = real.getForServing(key);
		boolean slow = slowServing.get();
		ServedStream record = new ServedStream();
		served.add(record);
		lastServedClosed = false;
		openServingStreams.incrementAndGet();
		return new StoredObject(new FilterInputStream(object.content()) {

			// FilterInputStream.read(byte[]) ends here; read() of one byte is not throttled (the controller never uses it)
			@Override
			public int read(byte[] buffer, int offset, int length) throws IOException {
				if (!slow) {
					return super.read(buffer, offset, length);
				}
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted.", e);
				}
				if (record.closed()) {
					throw new IOException("The stream is closed.");
				}
				return super.read(buffer, offset, Math.min(length, 64 * 1024));
			}

			@Override
			public void close() throws IOException {
				if (record.close()) {
					lastServedClosed = true;
					openServingStreams.decrementAndGet();
				}
				super.close();
			}
		}, object.length());
	}

	/** The configured store, without the spy. */
	public MediaObjectStore real() {
		return real;
	}

	@Override
	public void delete(Bucket bucket, String key) {
		if (failDeletes.get()) {
			throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
		}
		if (skipDeletes.get()) {
			return;
		}
		real.delete(bucket, key);
	}

	@Override
	public ObjectPage list(Bucket bucket, String prefix, String startAfterOrNull, int maxKeys) {
		if (bucket == Bucket.MEDIA && (emptyMediaBucket.get()
				|| (emptyPhotoPrefixes.get() && prefix.matches("media/[^/]+/")))) {
			return new ObjectPage(java.util.List.of(), false);
		}
		return real.list(bucket, prefix, startAfterOrNull, maxKeys);
	}

	@Override
	public void probe(Bucket bucket) {
		if (failProbe.get()) {
			throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
		}
		real.probe(bucket);
	}
}
