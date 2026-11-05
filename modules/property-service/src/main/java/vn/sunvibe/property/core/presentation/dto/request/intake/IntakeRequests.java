package vn.sunvibe.property.core.presentation.dto.request.intake;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** The bodies of the target and the availability (property-create intake spec 7.1). A note is the Sale's own text. */
public final class IntakeRequests {

    private static final int ID_LENGTH = 26;
    /** A property id is a UUID, longer than the ULIDs of the other rows. */
    private static final int PROPERTY_ID_LENGTH = 36;
    private static final int MAX_ITEMS = 50;
    private static final int MAX_ENUM = 40;
    /** A cap on what is read: decomposed text is longer. The use case keeps the limit of 500 on the NFC-trimmed text. */
    private static final int MAX_NOTE_READ = 1000;
    private static final String ID_ALPHABET = "[0-9A-Za-z-]*";

    private IntakeRequests() {
    }

    /** {@code canonicalSpaceId} only with CREATE; {@code propertyId} and {@code mappings} only with LINK. */
    @Schema(name = "IntakeTargetRequest")
    public record Target(@Schema(allowableValues = {"CREATE", "LINK"}) @NotBlank @Size(max = MAX_ENUM) String mode,
                         @Pattern(regexp = ID_ALPHABET) @Size(max = PROPERTY_ID_LENGTH) String propertyId,
                         @Pattern(regexp = ID_ALPHABET) @Size(max = ID_LENGTH) String canonicalSpaceId,
                         @Size(max = MAX_ITEMS) List<@Valid @NotNull Mapping> mappings, Long expectedVersion) {
    }

    @Schema(name = "IntakeMappingRequest")
    public record Mapping(@NotBlank @Pattern(regexp = ID_ALPHABET) @Size(max = ID_LENGTH) String spaceId,
                          @NotBlank @Pattern(regexp = ID_ALPHABET) @Size(max = ID_LENGTH) String unitId) {
    }

    @Schema(name = "IntakeCompletionRequest")
    public record Completion(Long expectedVersion, @NotBlank @Size(max = 64) String previewHash) {
    }

    @Schema(name = "IntakeRestorationRequest")
    public record Restoration(Long expectedVersion) {
    }

    @Schema(name = "IntakeAvailabilityRequest")
    public record Availability(@NotEmpty @Size(max = MAX_ITEMS) List<@Valid @NotNull Item> items, Long expectedVersion) {
    }

    @Schema(name = "IntakeAvailabilityItemRequest")
    public record Item(@NotBlank @Pattern(regexp = ID_ALPHABET) @Size(max = ID_LENGTH) String spaceId,
                       @Schema(allowableValues = {"VACANT", "OCCUPIED", "UNDER_REPAIR", "PAUSED", "OWNER_UNSURE", "SOURCE_CONFLICT"})
                       @NotBlank @Size(max = MAX_ENUM) String state,
                       @NotNull LocalDate contactedOn, @Size(max = MAX_NOTE_READ) String note, LocalDate expectedDate) {

        @Override
        public String toString() {
            return "Item[" + spaceId + "]";
        }
    }
}
