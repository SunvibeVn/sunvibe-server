package vn.sunvibe.survey.core.infrastructure.media.store;

import com.github.f4b6a3.ulid.UlidCreator;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.application.shared.external.media.StoreObjectNotFoundException;
import vn.sunvibe.survey.core.application.shared.external.media.StoreUnavailableException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * The media store on a local directory: dev, test and lan only (survey-media spec A-1). Each bucket is a directory;
 * a create-only put writes a temporary file and links it to the key, which fails when the key exists. Errors carry a
 * code only: an I/O message holds the path, so it is never attached.
 */
public class LocalMediaObjectStore implements MediaObjectStore {

    private static final String TEMP_PREFIX = ".tmp-";

    private final Path root;

    public LocalMediaObjectStore(Path root) {
        this.root = root;
    }

    @Override
    public PutOutcome putCreateOnly(Bucket bucket, String key, InputStream content, long length) {
        Path target = path(bucket, key);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = target.resolveSibling(TEMP_PREFIX + UlidCreator.getMonotonicUlid());
            long written;
            try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW)) {
                written = content.transferTo(out);
            }
            if (written != length) {
                throw new StoreUnavailableException(StoreUnavailableException.WRONG_ANSWER);
            }
            // survey-media spec I-3: a hard link is created only when the key does not exist, in one step; a move is no
            // create-only write on Linux, where rename replaces its target (SV-068). The temporary name is removed
            // in finally.
            Files.createLink(target, temp);
            return PutOutcome.CREATED;
        } catch (FileAlreadyExistsException e) {
            return PutOutcome.EXISTS;
        } catch (IOException e) {
            throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
        } finally {
            deleteQuietly(temp);
        }
    }

    @Override
    public StoredObject get(Bucket bucket, String key) {
        if (!Files.isDirectory(root)) {
            // the store itself is missing: unavailable, not a missing key
            throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
        }
        Path path = path(bucket, key);
        try {
            return new StoredObject(Files.newInputStream(path), Files.size(path));
        } catch (NoSuchFileException e) {
            throw new StoreObjectNotFoundException();
        } catch (IOException e) {
            throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
        }
    }

    @Override
    public StoredObject getForServing(String key) {
        return get(Bucket.MEDIA, key);
    }

    @Override
    public void delete(Bucket bucket, String key) {
        try {
            Files.deleteIfExists(path(bucket, key));
        } catch (IOException e) {
            throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
        }
    }

    @Override
    public ObjectPage list(Bucket bucket, String prefix, String startAfterOrNull, int maxKeys) {
        Path base = root.resolve(directory(bucket));
        if (!Files.isDirectory(base)) {
            return new ObjectPage(List.of(), false);
        }
        // walk only the prefix's directory: keys under "a/b/" are files under base/a/b
        int slash = prefix.lastIndexOf('/');
        Path start = slash < 0 ? base : base.resolve(prefix.substring(0, slash)).normalize();
        if (!start.startsWith(base) || !Files.isDirectory(start)) {
            return new ObjectPage(List.of(), false);
        }
        List<ListedObject> matches = new ArrayList<>();
        try (Stream<Path> files = Files.walk(start)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String key = base.relativize(file).toString().replace('\\', '/');
                if (file.getFileName().toString().startsWith(TEMP_PREFIX) || !key.startsWith(prefix)
                        || (startAfterOrNull != null && key.compareTo(startAfterOrNull) <= 0)) {
                    continue;
                }
                matches.add(new ListedObject(key, Files.getLastModifiedTime(file).toInstant(), Files.size(file)));
            }
        } catch (IOException e) {
            throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
        }
        matches.sort((a, b) -> a.key().compareTo(b.key()));
        boolean truncated = matches.size() > maxKeys;
        return new ObjectPage(List.copyOf(matches.subList(0, Math.min(maxKeys, matches.size()))), truncated);
    }

    @Override
    public void probe(Bucket bucket) {
        String key = (bucket == Bucket.QUARANTINE ? "quarantine/health/" : "health/") + UlidCreator.getMonotonicUlid();
        byte[] sample = ("probe " + key).getBytes(StandardCharsets.UTF_8);
        if (putCreateOnly(bucket, key, new java.io.ByteArrayInputStream(sample), sample.length)
                != PutOutcome.CREATED) {
            throw new StoreUnavailableException(StoreUnavailableException.WRONG_ANSWER);
        }
        try (StoredObject read = get(bucket, key)) {
            if (!Arrays.equals(sample, read.content().readAllBytes())) {
                throw new StoreUnavailableException(StoreUnavailableException.WRONG_ANSWER);
            }
        } catch (IOException e) {
            throw new StoreUnavailableException(StoreUnavailableException.SERVER_ERROR);
        } finally {
            delete(bucket, key);
        }
    }

    private Path path(Bucket bucket, String key) {
        if (key.isEmpty() || key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            throw new IllegalArgumentException("Not a store key.");
        }
        return root.resolve(directory(bucket)).resolve(key);
    }

    private static String directory(Bucket bucket) {
        return bucket == Bucket.QUARANTINE ? "quarantine-bucket" : "media-bucket";
    }

    private static void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // a leftover temporary name is skipped by listings and nothing removes it; after a put that
                // created its key it is a second name of the key's bytes (dev, test and lan only, survey-media spec A-1)
            }
        }
    }
}
