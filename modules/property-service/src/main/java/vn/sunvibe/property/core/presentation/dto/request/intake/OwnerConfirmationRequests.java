package vn.sunvibe.property.core.presentation.dto.request.intake;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The bodies of the owner request and the owner's verbs (property-create intake spec 7.1, 7.3). None of them shows its values in
 * {@code toString}: they hold an email, a code or a password.
 */
public final class OwnerConfirmationRequests {

    private static final int MAX_CODE = 32;
    private static final int MAX_PASSWORD = 256;
    private static final int MAX_EMAIL = 320;
    private static final int MAX_STATEMENT = 40;

    private OwnerConfirmationRequests() {
    }

    @Schema(name = "OwnerRequestAsk")
    public record Ask(@NotBlank @Size(max = MAX_EMAIL) String ownerEmail, Long expectedVersion) {

        @Override
        public String toString() {
            return "Ask[]";
        }
    }

    @Schema(name = "OwnerConfirmationCodeRequest")
    public record Code(@NotBlank @Size(max = MAX_CODE) String code) {

        @Override
        public String toString() {
            return "Code[]";
        }
    }

    @Schema(name = "OwnerConfirmationPhraseRequest")
    public record Phrase(@NotBlank @Size(max = MAX_CODE) String phrase) {

        @Override
        public String toString() {
            return "Phrase[]";
        }
    }

    @Schema(name = "OwnerConfirmationConfirmRequest")
    public record Confirm(@NotBlank @Size(max = MAX_CODE) String code,
                          @NotBlank @Size(max = MAX_PASSWORD) String currentPassword,
                          @NotBlank @Size(max = MAX_STATEMENT) String statementVersion) {

        @Override
        public String toString() {
            return "Confirm[]";
        }
    }

    @Schema(name = "OwnerConfirmationWithdrawalRequest")
    public record Withdrawal(@NotBlank @Size(max = MAX_PASSWORD) String currentPassword) {

        @Override
        public String toString() {
            return "Withdrawal[]";
        }
    }
}
