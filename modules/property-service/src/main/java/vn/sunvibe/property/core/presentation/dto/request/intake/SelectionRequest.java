package vn.sunvibe.property.core.presentation.dto.request.intake;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The parts an intake takes (property-create intake spec 7.1). */
public record SelectionRequest(
        @NotEmpty
        @Size(max = 50)
        @Schema(description = "1..50 space ids of the PASSED; a WHOLE source takes its one space")
        List<@Size(max = 26) String> spaceIds,
        @Schema(nullable = true, description = "The block's version; null only with no current row")
        Long expectedVersion) {
}
