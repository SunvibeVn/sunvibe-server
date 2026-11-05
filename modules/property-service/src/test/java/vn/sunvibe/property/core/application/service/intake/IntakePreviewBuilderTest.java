package vn.sunvibe.property.core.application.service.intake;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.sunvibe.property.core.application.exception.IntakeError;
import vn.sunvibe.property.core.application.exception.IntakeRefusedException;
import vn.sunvibe.property.core.application.shared.external.survey.IntakeSource;

/** property-create intake S-30: G1 text with U+0000 answers INTAKE_SOURCE_INVALID, at the preview and at completion. */
class IntakePreviewBuilderTest {

    private static IntakeSource source(String address, String location) {
        IntakeSource.Space space = new IntakeSource.Space("S1", "A1", location, null, LocalDate.EPOCH, null, 1, 1, null, List.of(),
                List.of());
        return new IntakeSource("R1", Instant.EPOCH, "U1", "U1", "sha", "WHOLE", "HOUSE", address, "48", "Đà Nẵng", "20194",
                "Hải Châu", null, null, LocalDate.EPOCH, List.of(space), List.of(), true, null, List.of());
    }

    @Test
    void preview_nulInSource_409() {
        IntakeRefusedException inAddress = assertThrows(IntakeRefusedException.class,
                () -> IntakePreviewBuilder.requireStorable(source("1\u0000 Bach Dang", "Floor 1")));
        IntakeRefusedException inSpace = assertThrows(IntakeRefusedException.class,
                () -> IntakePreviewBuilder.requireStorable(source("1 Bach Dang", "Fl\u0000or 1")));

        assertEquals(IntakeError.INTAKE_SOURCE_INVALID, inAddress.error());
        assertEquals(IntakeError.INTAKE_SOURCE_INVALID, inSpace.error());
    }

    @Test
    void cleanSource_passes() {
        assertDoesNotThrow(() -> IntakePreviewBuilder.requireStorable(source("1 Bach Dang", "Floor 1")));
    }
}
