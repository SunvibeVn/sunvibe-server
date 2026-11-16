package vn.sunvibe.brokerage.core.domain.model.agreement;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The public photo consent of a version (brokerage-agreement BR-9, BR-12; E05-S3 spec S3-D7): interior and common areas
 * yes, no or not answered yet; the exterior a separate choice, possible only inside a yes.
 */
class PhotoConsentTest {

    @Test
    void photoConsent_exteriorOnlyWithInterior() {
        assertDoesNotThrow(() -> new PhotoConsent(true, true));
        assertDoesNotThrow(() -> new PhotoConsent(true, false));
        assertDoesNotThrow(() -> new PhotoConsent(false, false));
        assertDoesNotThrow(() -> new PhotoConsent(null, false));

        assertEquals("exteriorPhotoConsent",
                assertThrows(AgreementContentException.class, () -> new PhotoConsent(null, true)).field());
        assertEquals("exteriorPhotoConsent",
                assertThrows(AgreementContentException.class, () -> new PhotoConsent(false, true)).field());
    }

    @Test
    void photoConsent_none_notAnsweredAndNoExterior() {
        assertNull(PhotoConsent.NONE.interiorOrNull());
        assertFalse(PhotoConsent.NONE.exterior());
    }
}
