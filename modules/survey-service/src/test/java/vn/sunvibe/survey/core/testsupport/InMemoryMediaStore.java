package vn.sunvibe.survey.core.testsupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.StoreObjectNotFoundException;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;

/** The private object store in memory: create-only puts, deletes recorded, a switch to fail puts like a 5xx. */
public final class InMemoryMediaStore implements MediaObjectStore {

    public final Map<String, byte[]> objects = new LinkedHashMap<>();
    public final List<String> deleted = new ArrayList<>();
    public boolean failPuts;
    /** Listings fail like a store that does not answer. */
    public boolean failLists;
    /** Runs at the start of every listing, so a test can act while a job is between its claim and its answer. */
    public Runnable beforeList = () -> { };
    /** Probes fail like a store that does not answer (spec A-18). */
    public volatile boolean failProbe;
    /** The buckets probed, in order. */
    public final List<Bucket> probes = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<String> journal;

    public InMemoryMediaStore(List<String> journal) {
        this.journal = journal;
    }

    private static String path(Bucket bucket, String key) {
        return bucket + ":" + key;
    }

    @Override
    public PutOutcome putCreateOnly(Bucket bucket, String key, InputStream content, long length) {
        journal.add("store:put");
        if (failPuts) {
            throw new StoreUnavailableException("STORE_5XX");
        }
        if (objects.containsKey(path(bucket, key))) {
            return PutOutcome.EXISTS;
        }
        try {
            byte[] bytes = content.readAllBytes();
            if (bytes.length != length) {
                throw new IllegalStateException("put of " + bytes.length + " bytes declared " + length);
            }
            objects.put(path(bucket, key), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return PutOutcome.CREATED;
    }

    @Override
    public StoredObject get(Bucket bucket, String key) {
        byte[] bytes = objects.get(path(bucket, key));
        if (bytes == null) {
            throw new StoreObjectNotFoundException();
        }
        return new StoredObject(new ByteArrayInputStream(bytes), bytes.length);
    }

    @Override
    public StoredObject getForServing(String key) {
        return get(Bucket.MEDIA, key);
    }

    @Override
    public void delete(Bucket bucket, String key) {
        journal.add("store:delete");
        deleted.add(path(bucket, key));
        objects.remove(path(bucket, key));
    }

    @Override
    public ObjectPage list(Bucket bucket, String prefix, String startAfterOrNull, int maxKeys) {
        beforeList.run();
        if (failLists) {
            throw new StoreUnavailableException("STORE_5XX");
        }
        String start = path(bucket, prefix);
        List<String> keys = objects.keySet().stream().filter(path -> path.startsWith(start))
                .map(path -> path.substring(path.indexOf(':') + 1))
                .filter(key -> startAfterOrNull == null || key.compareTo(startAfterOrNull) > 0).sorted().toList();
        List<ListedObject> page = keys.stream().limit(maxKeys).map(key -> new ListedObject(key, null,
                objects.get(path(bucket, key)).length)).toList();
        return new ObjectPage(page, keys.size() > maxKeys);
    }

    @Override
    public void probe(Bucket bucket) {
        probes.add(bucket);
        if (failProbe) {
            throw new StoreUnavailableException("STORE_TIMEOUT");
        }
    }
}
