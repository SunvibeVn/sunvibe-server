package vn.sunvibe.listing.core.presentation.dto;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.presentation.dto.request.ListingFreshnessRecordRequest;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFreshnessRecordResponse;
import vn.sunvibe.listing.core.presentation.dto.response.ListingFreshnessResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The private note never reaches a log through the freshness bodies (listing-publish BR-17; E06-S3 spec E06S3-D10,
 * FR-10): the request and the two answers that hold it print no note, outcome or answer.
 */
class ListingFreshnessDtoTest {

    @Test
    void toString_requestAndResponses_identifiersOnly() {
        ListingFreshnessRecordRequest request = new ListingFreshnessRecordRequest(4L, "CALL", "AVAILABLE", true, false,
                "SECRET-NOTE");
        ListingFreshnessRecordResponse record = new ListingFreshnessRecordResponse(LocalDate.of(2027, 3, 1),
                Instant.parse("2027-03-01T02:10:00Z"), "CALL", "AVAILABLE", true, false, false, "SECRET-NOTE");
        ListingFreshnessResponse freshness = new ListingFreshnessResponse("01JGLISTING000000000000000",
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 11), LocalDate.of(2027, 2, 15), true, false, record);
        List<String> texts = List.of(request.toString(), record.toString(), freshness.toString());

        assertEquals(List.of(), Stream.of("SECRET-NOTE", "AVAILABLE", "true", "false")
                .filter(word -> texts.stream().anyMatch(text -> text.contains(word)))
                .toList());
    }
}
