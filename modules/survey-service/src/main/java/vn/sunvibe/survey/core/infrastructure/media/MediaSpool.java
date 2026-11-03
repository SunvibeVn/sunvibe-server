package vn.sunvibe.survey.core.infrastructure.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.f4b6a3.ulid.UlidCreator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import vn.sunvibe.survey.core.application.exception.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.shared.MediaClock;
import vn.sunvibe.survey.core.application.shared.external.media.MediaBodySpool;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;

/**
 * The private spool of raw photo bodies (survey-media spec 4.4 step 2, A-13, A-17; ADR-011 decision 3; plan PD-12): an
 * owner-only directory per instance under the configured spool directory (0700, or an owner-only ACL where POSIX
 * permissions are not available, as on a Windows dev machine), one file per body with a random name, deleted when the
 * body is closed or refused. Each instance holds a lock on its directory while it runs; at start-up it sweeps its own
 * directory and those of instances whose lock is free (stopped or crashed), never a running instance's bodies (code
 * review M8, SV-069). The bytes are counted and hashed while they are written; the total deadline is checked between
 * reads.
 */
@Component
public class MediaSpool implements MediaBodySpool {

    private static final Logger log = LoggerFactory.getLogger(MediaSpool.class);
    private static final int BUFFER = 64 * 1024;
    private static final String LOCK = ".lock";
    private static final Pattern INSTANCE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    /** survey-media spec A-17: a body file is gone within an hour of receipt; older earlier-layout files are swept. */
    private static final Duration ABANDONED_AFTER = Duration.ofHours(1);
    /**
     * The lock files held in this JVM. On Linux closing any channel of a file releases every lock the process holds
     * on it, so a lock held here is never opened a second time (several application contexts in one test JVM).
     */
    private static final Set<Path> HELD = ConcurrentHashMap.newKeySet();

    private final Path root;
    private final Path directory;
    private final MediaClock clock;
    private FileChannel lockChannel;
    private FileLock lock;

    @Autowired
    public MediaSpool(MediaProperties media, MediaClock clock) {
        this(Path.of(media.getSpoolDir()), media.getInstanceId(), clock);
    }

    public MediaSpool(Path root, String instanceId, MediaClock clock) {
        if (instanceId == null || !INSTANCE_ID.matcher(instanceId).matches()) {
            throw new IllegalArgumentException("The media instance id is letters, digits, '-' or '_' (1 to 64).");
        }
        this.root = root;
        this.directory = root.resolve(instanceId);
        this.clock = clock;
    }

    /**
     * Creates the owner-only directories, takes this instance's lock and deletes what a crashed run left: in this
     * instance's directory, in the directories of instances that no longer run and, from the earlier layout, body
     * files directly in the spool directory older than an hour.
     */
    @PostConstruct
    public synchronized void start() {
        try {
            Files.createDirectories(root);
            restrictToOwner(root);
            Files.createDirectories(directory);
            restrictToOwner(directory);
            Path lockFile = lockFile(directory);
            if (!HELD.add(lockFile)) {
                throw new IllegalStateException("Another running instance uses this media instance id.");
            }
            lockChannel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = tryLock(lockChannel);
            if (lock == null) {
                lockChannel.close();
                lockChannel = null;
                HELD.remove(lockFile);
                throw new IllegalStateException("Another running instance uses this media instance id.");
            }
            deleteBodies(directory);
            sweepStoppedInstances();
            sweepEarlierLayout();
        } catch (IOException e) {
            // a failed start runs no @PreDestroy: give the lock back here
            stop();
            throw new UncheckedIOException("The survey media spool directory cannot be prepared.", e);
        }
    }

    /** Releases this instance's lock; its directory is swept at the next start of any instance. */
    @PreDestroy
    public synchronized void stop() {
        try {
            if (lock != null) {
                lock.release();
            }
            if (lockChannel != null) {
                lockChannel.close();
            }
        } catch (IOException e) {
            log.warn("The survey media spool lock could not be released");
        } finally {
            if (lock != null || lockChannel != null) {
                HELD.remove(lockFile(directory));
            }
            lock = null;
            lockChannel = null;
        }
    }

    private void sweepStoppedInstances() throws IOException {
        try (DirectoryStream<Path> instances = Files.newDirectoryStream(root,
                path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))) {
            for (Path other : instances) {
                Path otherLock = lockFile(other);
                if (other.equals(directory) || HELD.contains(otherLock) || !Files.isRegularFile(otherLock)) {
                    // this instance, one running in this JVM, or one still creating its directory (no lock file yet)
                    continue;
                }
                try {
                    sweepStopped(other, otherLock);
                } catch (IOException e) {
                    // another instance may sweep the same directory at the same time: this start goes on
                    log.warn("A survey media spool directory of another instance could not be swept");
                }
            }
        }
    }

    /**
     * The directory of an instance whose lock is free: its bodies and its lock file go while the lock is held, so an
     * instance that starts with the same id meanwhile locks a new file of its own; the empty directory goes last.
     */
    private static void sweepStopped(Path other, Path otherLock) throws IOException {
        try (FileChannel channel = FileChannel.open(otherLock, StandardOpenOption.WRITE)) {
            FileLock free = tryLock(channel);
            if (free == null) {
                return;
            }
            try {
                deleteBodies(other);
                Files.deleteIfExists(otherLock);
            } finally {
                free.release();
            }
        }
        try {
            Files.deleteIfExists(other);
        } catch (IOException e) {
            // an instance with this id started meanwhile; its own start swept the directory
        }
    }

    private void sweepEarlierLayout() throws IOException {
        Instant before = Instant.now().minus(ABANDONED_AFTER);
        try (DirectoryStream<Path> left = Files.newDirectoryStream(root, Files::isRegularFile)) {
            for (Path file : left) {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(before)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    /** Every body file of one instance's directory; its lock file stays. */
    private static void deleteBodies(Path instanceDirectory) throws IOException {
        try (DirectoryStream<Path> left = Files.newDirectoryStream(instanceDirectory, Files::isRegularFile)) {
            for (Path file : left) {
                if (!file.getFileName().toString().equals(LOCK)) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private static Path lockFile(Path instanceDirectory) {
        return instanceDirectory.resolve(LOCK).toAbsolutePath().normalize();
    }

    /** The lock, or null when another process holds it. */
    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    @Override
    public SpooledBody receive(InputStream body, long length, Instant deadline) {
        Path file = null;
        try {
            file = directory.resolve("body-" + UlidCreator.getMonotonicUlid() + ".part");
            Files.createFile(file);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER];
            long left = length;
            try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.WRITE)) {
                while (left > 0) {
                    if (!clock.now().isBefore(deadline)) {
                        throw new MediaBodyIncompleteException();
                    }
                    int read = body.read(buffer, 0, (int) Math.min(buffer.length, left));
                    if (read < 0) {
                        throw new MediaBodyIncompleteException();
                    }
                    sha256.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    left -= read;
                }
            }
            Spooled spooled = new Spooled(file, HexFormat.of().formatHex(sha256.digest()), length);
            file = null;
            return spooled;
        } catch (IOException e) {
            // the client stopped, the read timed out or the disk failed: nothing is kept
            throw new MediaBodyIncompleteException();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            deleteQuietly(file);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // swept at the next start; no path in the log
            log.warn("A survey media spool file could not be deleted");
        }
    }

    /** 0700, or an ACL with one entry for the owner. */
    private static void restrictToOwner(Path path) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null) {
            AclEntry owner = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .setFlags(java.nio.file.attribute.AclEntryFlag.FILE_INHERIT,
                            java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT)
                    .build();
            acl.setAcl(List.of(owner));
        }
    }

    /** A body in its spool file; closing deletes the file. */
    private static final class Spooled implements SpooledBody {

        private final Path file;
        private final String sha256Hex;
        private final long length;

        Spooled(Path file, String sha256Hex, long length) {
            this.file = file;
            this.sha256Hex = sha256Hex;
            this.length = length;
        }

        @Override
        public String sha256Hex() {
            return sha256Hex;
        }

        @Override
        public long length() {
            return length;
        }

        @Override
        public InputStream open() {
            try {
                return Files.newInputStream(file);
            } catch (IOException e) {
                throw new UncheckedIOException("A spooled survey photo body cannot be read.", e);
            }
        }

        @Override
        public void close() {
            deleteQuietly(file);
        }

        /** The file path is private: ids only. */
        @Override
        public String toString() {
            return "SpooledBody[" + length + " bytes]";
        }
    }
}
