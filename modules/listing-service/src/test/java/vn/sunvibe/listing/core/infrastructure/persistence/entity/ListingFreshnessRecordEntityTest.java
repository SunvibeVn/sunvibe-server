package vn.sunvibe.listing.core.infrastructure.persistence.entity;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The private note of a freshness record never reaches a log through its entity (listing-publish BR-17; E06-S3 spec
 * FR-10, E06S3-D10): its text holds identifiers only.
 */
class ListingFreshnessRecordEntityTest {

    @Test
    void toString_entity_identifiersOnly() {
        ListingFreshnessRecordEntity entity = ListingFreshnessRecordEntity.builder()
                .id("01JGFRESHREC00000000000000")
                .listingId("01JGLISTING000000000000000")
                .listingRowVersion(7L)
                .contactDate(LocalDate.of(2027, 3, 1))
                .recordedAt(Instant.parse("2027-03-01T02:10:00Z"))
                .recordedBy("01JGSALE000000000000000000")
                .channel(FreshnessChannel.CALL)
                .outcome(FreshnessOutcome.NOT_AVAILABLE)
                .termsStillApply(false)
                .unitUnchanged(false)
                .valid(false)
                .note("SECRET-NOTE")
                .build();

        String text = entity.toString();

        assertEquals(List.of(true, true, true), List.of(text.contains("01JGFRESHREC00000000000000"),
                text.contains("01JGLISTING000000000000000"), text.contains("listingRowVersion=7")));
        assertEquals(List.of(), Stream.of("SECRET-NOTE", "NOT_AVAILABLE", "true", "false")
                .filter(text::contains)
                .toList());
    }
}
