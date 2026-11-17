package vn.sunvibe.brokerage.core.application.command;

import java.util.Objects;

/**
 * Send an agreement's draft to its owner (brokerage-agreement US-1 AC-5; E05-S4 spec S4-D2) against the data version the
 * caller read, so the Sale sends exactly what they read (BR-5).
 */
public record AgreementSendCommand(String agreementId, Long expectedDataVersion) {

    public AgreementSendCommand {
        Objects.requireNonNull(agreementId, "agreementId");
        Objects.requireNonNull(expectedDataVersion, "expectedDataVersion");
    }
}
