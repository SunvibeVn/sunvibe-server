package vn.sunvibe.survey.core.application.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import vn.sunvibe.survey.core.application.exception.MediaRateLimitedException;
import vn.sunvibe.survey.core.testsupport.MediaServiceHarness;

/** survey-media spec A-3 and 9 (configuration), code review M2 (SV-069): the configured bodies per user are enforced. */
class MediaBodySlotsTest {

    private final MediaServiceHarness harness = new MediaServiceHarness();

    @Test
    void acquire_configuredOneBodyPerUser_secondBody429BodiesUntilTheFirstEnds() {
        MediaBodySlots slots = new MediaBodySlots(harness.slots, harness.counters, harness.clock,
                harness.transactions, new MediaInstanceSettings(MediaServiceHarness.INSTANCE, 1, 16));

        try (MediaBodySlots.Lease first = slots.acquire(MediaServiceHarness.SALE_A)) {
            MediaRateLimitedException refused = assertThrows(MediaRateLimitedException.class,
                    () -> slots.acquire(MediaServiceHarness.SALE_A));
            assertEquals("bodies", refused.getField());
            slots.acquire(MediaServiceHarness.SALE_B).close();
        }
        slots.acquire(MediaServiceHarness.SALE_A).close();
    }
}
