package vn.sunvibe.survey.core.infrastructure.media.store;

import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;

import java.io.InputStream;
import java.util.List;

/**
 * The store while {@code app.survey.media.store=none} (survey-media spec A-1): no photo can exist and nothing is
 * scheduled, so no call is expected; one that comes anyway answers unavailable. A listing is empty.
 */
public class UnconfiguredMediaObjectStore implements MediaObjectStore {

    @Override
    public PutOutcome putCreateOnly(Bucket bucket, String key, InputStream content, long length) {
        throw new StoreUnavailableException(StoreUnavailableException.NOT_CONFIGURED);
    }

    @Override
    public StoredObject get(Bucket bucket, String key) {
        throw new StoreUnavailableException(StoreUnavailableException.NOT_CONFIGURED);
    }

    @Override
    public StoredObject getForServing(String key) {
        throw new StoreUnavailableException(StoreUnavailableException.NOT_CONFIGURED);
    }

    @Override
    public void delete(Bucket bucket, String key) {
        throw new StoreUnavailableException(StoreUnavailableException.NOT_CONFIGURED);
    }

    @Override
    public ObjectPage list(Bucket bucket, String prefix, String startAfterOrNull, int maxKeys) {
        return new ObjectPage(List.of(), false);
    }

    @Override
    public void probe(Bucket bucket) {
        throw new StoreUnavailableException(StoreUnavailableException.NOT_CONFIGURED);
    }
}
