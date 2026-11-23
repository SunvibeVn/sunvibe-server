package vn.sunvibe.shared.outbox;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The listing's event (listing-publish E06-S6 spec E06S6-D9; PL-13 unit half; ADR-014 decision 4): the listing id only,
 * the same id as the aggregate's, never more.
 */
class ListingChangedEventTest {

    private static final String LISTING_ID = "01HZX3M5Q8R2T4V6W8Y0A2C4E6";

    @Test
    void now_listingId_typeVersionModuleAggregateAndPayload() {
        OutboxEvent event = ListingChangedEvent.now(LISTING_ID);

        assertEquals("listing.listing-changed", event.type());
        assertEquals(1, event.version());
        assertEquals("listing", event.sourceModule());
        assertEquals("listing", event.aggregateType());
        assertEquals(LISTING_ID, event.aggregateId());
        assertEquals(Map.of("listingId", LISTING_ID), event.payload());
        assertEquals(event.aggregateId(), event.payload().get(ListingChangedEvent.LISTING_ID));
        assertNull(event.dueAt());
    }

    @Test
    void now_notAUlid_refused() {
        for (String listingId : new String[]{null, "", "   ", "01HZX3M5Q8R2T4V6W8Y0A2C4E", "01hzx3m5q8r2t4v6w8y0a2c4e6",
                "01HZX3M5Q8R2T4V6W8Y0A2C4EI", "01HZX3M5Q8R2T4V6W8Y0A2C4EU", "01HZX3M5Q8R2T4V6W8Y0A2C4 6"}) {
            assertThrows(IllegalArgumentException.class, () -> ListingChangedEvent.now(listingId),
                    String.valueOf(listingId));
        }
    }
}
