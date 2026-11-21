package vn.sunvibe.listing.core.application.query;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The private note never reaches a log through the freshness read (listing-publish BR-17; E06-S3 spec E06S3-D10): the
 * texts of the view and of its latest record hold identifiers only.
 */
class ListingFreshnessViewTest {

    @Test
    void toString_viewAndLatest_identifiersOnly() {
        ListingFreshnessView.LatestRecord latest = new ListingFreshnessView.LatestRecord(LocalDate.of(2027, 3, 1),
                Instant.parse("2027-03-01T02:10:00Z"), FreshnessChannel.CALL, FreshnessOutcome.AVAILABLE, true, false,
                false, "SECRET-NOTE");
        ListingFreshnessView view = new ListingFreshnessView("01JGLISTING000000000000000", LocalDate.of(2027, 2, 1),
                LocalDate.of(2027, 2, 11), LocalDate.of(2027, 2, 15), true, true, latest);
        List<String> texts = List.of(view.toString(), latest.toString());

        assertEquals(List.of(), Stream.of("SECRET-NOTE", "AVAILABLE", "true", "false")
                .filter(word -> texts.stream().anyMatch(text -> text.contains(word)))
                .toList());
    }
}
