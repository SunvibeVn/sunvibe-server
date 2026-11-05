package vn.sunvibe.property.core.presentation.dto.request.intake;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The bodies of the manager paths (property-create intake spec 7.1). Free text is checked by the use case, which names the field
 * and applies the phone rule. {@code basis} and the reasons are kept for managers, so {@code toString} shows none of them.
 */
public final class OwnerCheckRequests {

    private static final int ID_LENGTH = 26;
    /** A cap on what is read: decomposed text is longer. The use case keeps the limit of 2000 on the NFC-trimmed text. */
    private static final int MAX_TEXT_READ = 4000;
    private static final String ID_ALPHABET = "[0-9A-Za-z-]*";

    private OwnerCheckRequests() {
    }

    @Schema(name = "OwnerCheckRequest")
    public record Check(@NotBlank @Pattern(regexp = ID_ALPHABET) @Size(max = ID_LENGTH) String proofId,
                        @Size(max = MAX_TEXT_READ) String basis, Long expectedVersion) {

        @Override
        public String toString() {
            return "Check[" + proofId + "]";
        }
    }

    @Schema(name = "IntakeSuspensionRequest")
    public record Suspension(@Pattern(regexp = ID_ALPHABET) @Size(max = ID_LENGTH) String unitId,
                             @Size(max = MAX_TEXT_READ) String reason, Long expectedVersion) {

        @Override
        public String toString() {
            return "Suspension[" + unitId + "]";
        }
    }

    @Schema(name = "IntakeReleaseRequest")
    public record Release(@Size(max = MAX_TEXT_READ) String releaseReason, Long expectedVersion) {

        @Override
        public String toString() {
            return "Release[]";
        }
    }
}
