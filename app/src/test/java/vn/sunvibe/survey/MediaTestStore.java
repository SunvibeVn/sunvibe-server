package vn.sunvibe.survey;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;

/**
 * The media tests' store: the configured one behind a test-only spy (plan Global Constraints: switches vary at run
 * time). It counts the serving reads, can make them fail like a store without a free GET connection, and records
 * whether the last served stream was closed (the R2 stream released, spec A-25).
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
		lastServedClosed = false;
		openServingStreams.incrementAndGet();
		return new StoredObject(new FilterInputStream(object.content()) {
			private boolean closed;

			@Override
			public void close() throws IOException {
				if (!closed) {
					closed = true;
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
