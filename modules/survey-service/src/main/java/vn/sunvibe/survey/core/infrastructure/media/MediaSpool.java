package vn.sunvibe.survey.core.infrastructure.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
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
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.f4b6a3.ulid.UlidCreator;

import jakarta.annotation.PostConstruct;
import vn.sunvibe.survey.core.application.exception.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.shared.MediaClock;
import vn.sunvibe.survey.core.application.shared.external.media.MediaBodySpool;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;

/**
 * The private spool of raw photo bodies (survey-media spec 4.4 step 2, A-13; ADR-011 decision 3): an owner-only
 * directory (0700, or an owner-only ACL where POSIX permissions are not available, as on a Windows dev machine), one
 * file per body with a random name, deleted when the body is closed or refused, and leftovers of a crash swept at
 * start-up. The bytes are counted and hashed while they are written; the total deadline is checked between reads.
 */
@Component
public class MediaSpool implements MediaBodySpool {

    private static final Logger log = LoggerFactory.getLogger(MediaSpool.class);
    private static final int BUFFER = 64 * 1024;

    private final Path directory;
    private final MediaClock clock;

    @Autowired
    public MediaSpool(MediaProperties media, MediaClock clock) {
        this(Path.of(media.getSpoolDir()), clock);
    }

    public MediaSpool(Path directory, MediaClock clock) {
        this.directory = directory;
        this.clock = clock;
    }

    /** Creates the owner-only directory and deletes what a crashed run left in it. */
    @PostConstruct
    public void start() {
        try {
            Files.createDirectories(directory);
            restrictToOwner(directory);
            try (DirectoryStream<Path> left = Files.newDirectoryStream(directory)) {
                for (Path file : left) {
                    if (Files.isRegularFile(file)) {
                        Files.deleteIfExists(file);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The survey media spool directory cannot be prepared.", e);
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
