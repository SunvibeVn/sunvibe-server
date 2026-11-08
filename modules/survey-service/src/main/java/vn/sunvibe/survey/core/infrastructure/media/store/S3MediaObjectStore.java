package vn.sunvibe.survey.core.infrastructure.media.store;

import com.github.f4b6a3.ulid.UlidCreator;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.StoreObjectNotFoundException;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * The media store on the S3 API of Cloudflare R2 (survey-media spec 4.12, ADR-011 decision 1). Every put carries
 * {@code If-None-Match: *}, so a key is written once (a 412 is EXISTS); deletes and listings are strongly consistent on
 * R2. SDK exceptions become codes here and are never logged or attached: their messages hold keys and buckets.
 */
public class S3MediaObjectStore implements MediaObjectStore, AutoCloseable {

    private static final int PRECONDITION_FAILED = 412;
    private static final int NOT_FOUND = 404;

    private final S3ClientSettings settings;
    private final S3Client get;
    private final S3Client background;
    private final S3Client probe;

    S3MediaObjectStore(S3ClientSettings settings, S3Client get, S3Client background, S3Client probe) {
        this.settings = settings;
        this.get = get;
        this.background = background;
        this.probe = probe;
    }

    /** A store with its three bounded clients. */
    public static S3MediaObjectStore create(S3ClientSettings settings) {
        return new S3MediaObjectStore(settings, S3ClientsConfig.getClient(settings),
                S3ClientsConfig.backgroundClient(settings), S3ClientsConfig.probeClient(settings));
    }

    @Override
    public PutOutcome putCreateOnly(Bucket bucket, String key, InputStream content, long length) {
        return put(background, bucket, key, content, length);
    }

    @Override
    public StoredObject get(Bucket bucket, String key) {
        return open(background, bucket, key);
    }

    @Override
    public StoredObject getForServing(String key) {
        return open(get, Bucket.MEDIA, key);
    }

    @Override
    public void delete(Bucket bucket, String key) {
        delete(background, bucket, key);
    }

    @Override
    public ObjectPage list(Bucket bucket, String prefix, String startAfterOrNull, int maxKeys) {
        try {
            ListObjectsV2Response page = background.listObjectsV2(request -> request.bucket(name(bucket))
                    .prefix(prefix).startAfter(startAfterOrNull).maxKeys(maxKeys));
            List<ListedObject> objects = page.contents().stream()
                    .map(object -> new ListedObject(object.key(), object.lastModified(), object.size())).toList();
            return new ObjectPage(objects, Boolean.TRUE.equals(page.isTruncated()));
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void probe(Bucket bucket) {
        String key = (bucket == Bucket.QUARANTINE ? "quarantine/health/" : "health/") + UlidCreator.getMonotonicUlid();
        byte[] sample = ("probe " + key).getBytes(StandardCharsets.UTF_8);
        if (put(probe, bucket, key, new ByteArrayInputStream(sample), sample.length) != PutOutcome.CREATED) {
            throw new StoreUnavailableException(StoreUnavailableException.WRONG_ANSWER);
        }
        try (StoredObject read = open(probe, bucket, key)) {
            if (!Arrays.equals(sample, read.content().readAllBytes())) {
                throw new StoreUnavailableException(StoreUnavailableException.WRONG_ANSWER);
            }
        } catch (StoreObjectNotFoundException e) {
            throw new StoreUnavailableException(StoreUnavailableException.WRONG_ANSWER);
        } catch (IOException e) {
            throw new StoreUnavailableException(StoreUnavailableException.CONNECTION);
        } finally {
            delete(probe, bucket, key);
        }
    }

    @Override
    public void close() {
        get.close();
        background.close();
        probe.close();
    }

    private PutOutcome put(S3Client client, Bucket bucket, String key, InputStream content, long length) {
        try {
            client.putObject(request -> request.bucket(name(bucket)).key(key).ifNoneMatch("*")
                    .contentLength(length), RequestBody.fromInputStream(content, length));
            return PutOutcome.CREATED;
        } catch (S3Exception e) {
            if (e.statusCode() == PRECONDITION_FAILED) {
                return PutOutcome.EXISTS;
            }
            throw unavailable(e);
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    private StoredObject open(S3Client client, Bucket bucket, String key) {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(request -> request.bucket(name(bucket))
                    .key(key));
            // survey-media spec A-25: an abort drops the connection; a close would read the rest of the object first
            return new StoredObject(stream, stream.response().contentLength(), stream::abort);
        } catch (NoSuchKeyException e) {
            throw new StoreObjectNotFoundException();
        } catch (S3Exception e) {
            if (e.statusCode() == NOT_FOUND) {
                throw new StoreObjectNotFoundException();
            }
            throw unavailable(e);
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    private void delete(S3Client client, Bucket bucket, String key) {
        try {
            client.deleteObject(request -> request.bucket(name(bucket)).key(key));
        } catch (S3Exception e) {
            if (e.statusCode() != NOT_FOUND) {
                throw unavailable(e);
            }
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    private String name(Bucket bucket) {
        return bucket == Bucket.QUARANTINE ? settings.quarantineBucket() : settings.mediaBucket();
    }

    /** Maps an SDK failure to a code; the SDK exception is dropped, never attached or logged. */
    static StoreUnavailableException unavailable(SdkException e) {
        if (e instanceof S3Exception service) {
            int status = service.statusCode();
            return new StoreUnavailableException(status >= 500 ? StoreUnavailableException.SERVER_ERROR
                    : StoreUnavailableException.REFUSED);
        }
        if (e instanceof SdkClientException) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause.getClass().getName().endsWith("ConnectionPoolTimeoutException")) {
                    return new StoreUnavailableException(StoreUnavailableException.POOL_EXHAUSTED);
                }
            }
        }
        return new StoreUnavailableException(StoreUnavailableException.CONNECTION);
    }
}
