package vn.sunvibe.listing.core.application.command;

import org.junit.jupiter.api.Test;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessChannel;
import vn.sunvibe.listing.core.domain.model.listing.FreshnessOutcome;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The private note never reaches a log through the record command (listing-publish BR-17; E06-S3 spec E06S3-D10): its
 * text holds identifiers only.
 */
class FreshnessRecordCommandTest {

    @Test
    void toString_command_identifiersOnly() {
        String text = new FreshnessRecordCommand("01JGLISTING000000000000000", 4L, FreshnessChannel.MESSAGE,
                FreshnessOutcome.AVAILABLE, true, false, "SECRET-NOTE").toString();

        assertEquals(List.of(), Stream.of("SECRET-NOTE", "AVAILABLE", "true", "false")
                .filter(text::contains)
                .toList());
    }
}
