package vn.sunvibe.brokerage.core.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The owner confirms one or several versions sent to them with one password (brokerage-agreement US-2 AC-2, AC-4; E05-S5
 * spec S5-D5, 4). Unknown properties are ignored: the account is the signed-in one, never a body field.
 */
@Schema(name = "BrokerageAgreementConfirmationRequest")
public record BrokerageAgreementConfirmationRequest(
        @NotBlank @Size(max = 256)
        @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, description = "The account's current password")
        String currentPassword,
        @NotBlank @Size(max = 40)
        @Schema(description = "The statement version the owner read", example = "brokerage-agreement-2026-11")
        String statementVersion,
        @NotNull @Size(min = 1, max = 20) @Valid
        @Schema(description = "1 to 20 versions, no agreement twice; each recorded on its own, all or none")
        List<@NotNull BrokerageAgreementConfirmationItemInput> versions) {

    /** No password and no statement in any log line. */
    @Override
    public String toString() {
        return "BrokerageAgreementConfirmationRequest[" + (versions == null ? 0 : versions.size()) + " versions]";
    }
}
