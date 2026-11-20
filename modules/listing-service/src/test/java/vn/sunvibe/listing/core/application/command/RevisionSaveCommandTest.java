package vn.sunvibe.listing.core.application.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A save carries unpublished text (listing-publish US-10 AC-4; E06-S2 spec E06S2-D21): its text names none of it. */
class RevisionSaveCommandTest {

    @Test
    void toString_saveCommandAndFeatures_hidden() {
        RevisionFeaturesInput features = new RevisionFeaturesInput("FULL", true, false, true, "PRIVATE", "SHARED", true,
                false, true, false, true, "ALLOWED", true);
        RevisionSaveCommand command = new RevisionSaveCommand("01JLISTING0000000000000001", 1, 3L, "secret title",
                "secret description", features);

        assertEquals("RevisionSaveCommand[hidden]", command.toString());
        assertEquals("RevisionFeaturesInput[hidden]", features.toString());
        assertFalse((command + " " + features).contains("secret"));
        assertFalse((command + " " + features).contains("FULL"));
    }
}
