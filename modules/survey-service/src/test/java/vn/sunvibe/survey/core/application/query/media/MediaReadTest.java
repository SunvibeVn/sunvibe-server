package vn.sunvibe.survey.core.application.query.media;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * A content read's two ends (survey-media spec A-25; code review I1): the watchdog's abort ends only the store stream,
 * the request thread's close releases the slots, each once.
 */
class MediaReadTest {

    private final AtomicInteger aborts = new AtomicInteger();
    private final AtomicInteger slotReleases = new AtomicInteger();
    private final MediaRead read = new MediaRead("01JMED1A000000000000000001", new ByteArrayInputStream(new byte[] {1}),
            1, false, Duration.ofSeconds(30), Instant.now().plusSeconds(60), slotReleases::incrementAndGet,
            aborts::incrementAndGet);

    @Test
    void abortContent_endsTheStreamOnly_slotsStayTaken() {
        read.abortContent();
        read.abortContent();

        assertEquals(1, aborts.get());
        assertEquals(0, slotReleases.get(), "the request thread still holds the slots");
    }

    @Test
    void close_releasesTheSlotsOnce_noAbortAfterward() {
        read.close();
        read.close();
        read.abortContent();

        assertEquals(1, slotReleases.get());
        assertEquals(0, aborts.get());
    }
}
