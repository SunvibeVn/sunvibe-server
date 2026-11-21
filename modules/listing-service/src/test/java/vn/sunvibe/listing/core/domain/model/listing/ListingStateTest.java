package vn.sunvibe.listing.core.domain.model.listing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The listing states and revision statuses of listing sections 3.2 and 3.3, and the state a read answers (E06-S1 spec
 * E06S1-D5: a listing whose agreement ended reads as archived, ADR-012 decision 2).
 */
class ListingStateTest {

    @Test
    void effective_agreementEnded_archivedFromEveryState() {
        for (ListingState stored : ListingState.values()) {
            assertEquals(ListingState.ARCHIVED, stored.effective(true), stored.name());
        }
    }

    @Test
    void effective_agreementNotEnded_storedState() {
        for (ListingState stored : ListingState.values()) {
            assertEquals(stored, stored.effective(false), stored.name());
        }
    }

    @Test
    void codes_listingAndRevisionStates_theBriefVocabulary() {
        assertEquals(List.of("UNPUBLISHED", "PUBLISHED", "PAUSED", "URGENTLY_UNPUBLISHED", "ARCHIVED"),
                Arrays.stream(ListingState.values()).map(Enum::name).toList());
        assertEquals(List.of("DRAFT", "PENDING_REVIEW", "NEEDS_CHANGES", "REJECTED", "APPROVED", "LIVE", "REPLACED",
                        "CANCELLED"),
                Arrays.stream(RevisionStatus.values()).map(Enum::name).toList());
    }
}
