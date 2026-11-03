package vn.sunvibe.survey.core.infrastructure.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vn.sunvibe.survey.core.application.exception.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.shared.external.media.MediaBodySpool.SpooledBody;
import vn.sunvibe.survey.core.testsupport.InMemoryBodySpool;

/**
 * The private spool of raw bodies (survey-media spec 4.4 step 2, A-13; ADR-011 decision 3): an owner-only directory
 * (0700, or an owner-only ACL where POSIX permissions do not exist), random file names, each file deleted when its
 * body is closed or refused, and leftovers swept at start-up.
 */
class MediaSpoolTest {

    private static final byte[] BYTES = "raw photo bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temp;

    private Instant now = Instant.parse("2026-11-02T03:00:00Z");

    @Test
    void directory_ownerOnly_posixOrAcl() throws Exception {
        Path dir = temp.resolve("spool");
        new MediaSpool(dir, () -> now).start();

        assertTrue(Files.isDirectory(dir));
        if (Files.getFileAttributeView(dir, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(dir, AclFileAttributeView.class);
            List<AclEntry> entries = acl.getAcl();
            assertEquals(1, entries.size(), entries.toString());
            assertEquals(acl.getOwner(), entries.get(0).principal());
        }
    }

    @Test
    void files_deletedInFinallyAndSweptAtStart() throws Exception {
        Path dir = temp.resolve("spool");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("left-by-a-crash.part"), "stale");
        MediaSpool spool = new MediaSpool(dir, () -> now);
        spool.start();
        assertEquals(0, files(dir), "swept at start-up");

        try (SpooledBody body = spool.receive(new ByteArrayInputStream(BYTES), BYTES.length,
                now.plusSeconds(600))) {
            assertEquals(1, files(dir));
            assertEquals(InMemoryBodySpool.sha256(BYTES), body.sha256Hex());
            assertEquals(BYTES.length, body.length());
            try (InputStream in = body.open()) {
                assertEquals(new String(BYTES, StandardCharsets.UTF_8), new String(in.readAllBytes(),
                        StandardCharsets.UTF_8));
            }
        }
        assertEquals(0, files(dir), "deleted when closed");

        assertThrows(MediaBodyIncompleteException.class, () -> spool.receive(new ByteArrayInputStream(new byte[3]),
                BYTES.length, now.plusSeconds(600)));
        assertEquals(0, files(dir), "deleted when the body ended early");

        assertThrows(MediaBodyIncompleteException.class, () -> spool.receive(new ByteArrayInputStream(BYTES),
                BYTES.length, now.minusSeconds(1)));
        assertEquals(0, files(dir), "deleted when the deadline passed");
    }

    @Test
    void fileNames_areRandom() throws Exception {
        Path dir = temp.resolve("spool");
        MediaSpool spool = new MediaSpool(dir, () -> now);
        spool.start();

        try (SpooledBody first = spool.receive(new ByteArrayInputStream(BYTES), BYTES.length, now.plusSeconds(60));
             SpooledBody second = spool.receive(new ByteArrayInputStream(BYTES), BYTES.length,
                     now.plusSeconds(60))) {
            try (Stream<Path> listed = Files.list(dir)) {
                List<String> names = listed.map(path -> path.getFileName().toString()).toList();
                assertEquals(2, names.size());
                assertFalse(names.get(0).equals(names.get(1)));
            }
        }
    }

    private static long files(Path dir) throws Exception {
        try (Stream<Path> listed = Files.list(dir)) {
            return listed.count();
        }
    }
}
