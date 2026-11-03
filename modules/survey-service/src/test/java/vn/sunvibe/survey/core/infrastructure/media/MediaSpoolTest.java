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
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vn.sunvibe.survey.core.application.exception.MediaBodyIncompleteException;
import vn.sunvibe.survey.core.application.shared.external.media.MediaBodySpool.SpooledBody;
import vn.sunvibe.survey.core.testsupport.InMemoryBodySpool;

/**
 * The private spool of raw bodies (survey-media spec 4.4 step 2, A-13, A-17; ADR-011 decision 3; plan PD-12): an
 * owner-only directory per instance (0700, or an owner-only ACL where POSIX permissions do not exist) under the
 * configured spool directory, random file names, each file deleted when its body is closed or refused, and leftovers
 * swept at start-up: this instance's own, and those of instances that no longer run (their lock is free). Instances
 * sharing the spool directory never delete each other's bodies (code review M8, SV-069).
 */
class MediaSpoolTest {

    private static final byte[] BYTES = "raw photo bytes".getBytes(StandardCharsets.UTF_8);
    private static final String A = "01JSPOOLA00000000000000001";
    private static final String B = "01JSPOOLB00000000000000002";

    @TempDir
    Path temp;

    private final Instant now = Instant.parse("2026-11-02T03:00:00Z");
    private final List<MediaSpool> started = new java.util.ArrayList<>();

    @AfterEach
    void stopAll() {
        started.forEach(MediaSpool::stop);
    }

    @Test
    void directories_ownerOnly_posixOrAcl() throws Exception {
        Path root = temp.resolve("spool");
        start(root, A);

        for (Path dir : List.of(root, root.resolve(A))) {
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
    }

    @Test
    void files_deletedInFinallyAndSweptAtStart() throws Exception {
        Path root = temp.resolve("spool");
        Path own = root.resolve(A);
        Files.createDirectories(own);
        Files.writeString(own.resolve("body-left-by-a-crash.part"), "stale");
        MediaSpool spool = start(root, A);
        assertEquals(0, bodies(own), "swept at start-up");

        try (SpooledBody body = spool.receive(new ByteArrayInputStream(BYTES), BYTES.length,
                now.plusSeconds(600))) {
            assertEquals(1, bodies(own));
            assertEquals(InMemoryBodySpool.sha256(BYTES), body.sha256Hex());
            assertEquals(BYTES.length, body.length());
            try (InputStream in = body.open()) {
                assertEquals(new String(BYTES, StandardCharsets.UTF_8), new String(in.readAllBytes(),
                        StandardCharsets.UTF_8));
            }
        }
        assertEquals(0, bodies(own), "deleted when closed");

        assertThrows(MediaBodyIncompleteException.class, () -> spool.receive(new ByteArrayInputStream(new byte[3]),
                BYTES.length, now.plusSeconds(600)));
        assertEquals(0, bodies(own), "deleted when the body ended early");

        assertThrows(MediaBodyIncompleteException.class, () -> spool.receive(new ByteArrayInputStream(BYTES),
                BYTES.length, now.minusSeconds(1)));
        assertEquals(0, bodies(own), "deleted when the deadline passed");
    }

    @Test
    void fileNames_areRandom() throws Exception {
        Path root = temp.resolve("spool");
        MediaSpool spool = start(root, A);

        try (SpooledBody first = spool.receive(new ByteArrayInputStream(BYTES), BYTES.length, now.plusSeconds(60));
             SpooledBody second = spool.receive(new ByteArrayInputStream(BYTES), BYTES.length,
                     now.plusSeconds(60))) {
            List<String> names = bodyNames(root.resolve(A));
            assertEquals(2, names.size());
            assertFalse(names.get(0).equals(names.get(1)));
        }
    }

    @Test
    void start_secondInstanceOnSharedDirectory_keepsTheFirstInstancesBodies() throws Exception {
        Path root = temp.resolve("spool");
        MediaSpool first = start(root, A);

        try (SpooledBody body = first.receive(new ByteArrayInputStream(BYTES), BYTES.length, now.plusSeconds(60))) {
            start(root, B);

            assertEquals(1, bodies(root.resolve(A)), "the running instance's body is kept");
            try (InputStream in = body.open()) {
                assertEquals(BYTES.length, in.readAllBytes().length);
            }
        }
    }

    @Test
    void start_instanceThatNoLongerRuns_itsLeftoversSwept() throws Exception {
        Path root = temp.resolve("spool");
        MediaSpool crashed = start(root, A);
        crashed.receive(new ByteArrayInputStream(BYTES), BYTES.length, now.plusSeconds(60));
        crashed.stop();
        Files.writeString(root.resolve(A).resolve("body-left-by-a-crash.part"), "stale");

        start(root, B);

        assertFalse(Files.exists(root.resolve(A)), "a stopped instance's directory is swept with its bodies");
        assertTrue(Files.isDirectory(root.resolve(B)));
    }

    /** Another process (a second API container on a shared volume) keeps its bodies while it runs, not after it dies. */
    @Test
    void start_instanceInAnotherProcess_keptWhileItRunsSweptAfterItDies() throws Exception {
        Path root = temp.resolve("spool");
        Path other = root.resolve(A);
        Files.createDirectories(other);
        Files.writeString(other.resolve("body-in-flight.part"), "live");
        Path holder = temp.resolve("HoldLock.java");
        Files.writeString(holder, """
                import java.nio.channels.FileChannel;
                import java.nio.file.Path;
                import java.nio.file.StandardOpenOption;
                public class HoldLock {
                    public static void main(String[] args) throws Exception {
                        FileChannel channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE);
                        channel.lock();
                        System.out.println("locked");
                        Thread.sleep(60_000);
                    }
                }
                """);
        String java = ProcessHandle.current().info().command().orElseThrow();
        Process process = new ProcessBuilder(java, holder.toString(), other.resolve(".lock").toString())
                .redirectErrorStream(true).start();
        try {
            try (java.io.BufferedReader out = new java.io.BufferedReader(new java.io.InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                assertEquals("locked", out.readLine());
                start(root, B).stop();
                assertEquals(1, bodies(other), "a running instance in another process keeps its bodies");
            }
        } finally {
            process.destroyForcibly().waitFor();
        }

        start(root, B);

        assertFalse(Files.exists(other), "swept once that process is gone");
    }

    @Test
    void start_sameInstanceIdRunning_refusedToStart() {
        Path root = temp.resolve("spool");
        start(root, A);

        MediaSpool twin = new MediaSpool(root, A, () -> now);
        assertThrows(IllegalStateException.class, twin::start);
    }

    @Test
    void start_rootFilesOfTheEarlierLayout_sweptOnlyWhenOlderThanAnHour() throws Exception {
        Path root = temp.resolve("spool");
        Files.createDirectories(root);
        Path old = Files.writeString(root.resolve("body-old.part"), "stale");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofMinutes(61))));
        Path recent = Files.writeString(root.resolve("body-recent.part"), "maybe live");

        start(root, A);

        assertFalse(Files.exists(old), "spec A-17: gone at most an hour after receipt");
        assertTrue(Files.exists(recent), "a body of a running instance of the earlier version is kept");
    }

    @Test
    void instanceId_notOnePathSegment_refused() {
        Path root = temp.resolve("spool");

        for (String id : List.of("", "../x", "a/b", "a\\b", ".")) {
            assertThrows(IllegalArgumentException.class, () -> new MediaSpool(root, id, () -> now), id);
        }
    }

    private MediaSpool start(Path root, String instanceId) {
        MediaSpool spool = new MediaSpool(root, instanceId, () -> now);
        spool.start();
        started.add(spool);
        return spool;
    }

    private static long bodies(Path dir) throws Exception {
        return bodyNames(dir).size();
    }

    private static List<String> bodyNames(Path dir) throws Exception {
        try (Stream<Path> listed = Files.list(dir)) {
            return listed.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("body-"))
                    .toList();
        }
    }
}
