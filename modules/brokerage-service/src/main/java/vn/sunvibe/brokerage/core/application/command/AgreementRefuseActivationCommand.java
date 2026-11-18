package vn.sunvibe.brokerage.core.application.command;

import vn.sunvibe.brokerage.core.domain.model.agreement.ActivationRefusalType;

/**
 * An independent manager refuses to activate the version the owner confirmed, with its type and an optional note
 * (brokerage-agreement US-3 AC-3, E-7; E05-S6 spec S6-D4).
 *
 * @param noteOrNull the note as written; private (brokerage-agreement BR-17)
 */
public record AgreementRefuseActivationCommand(String agreementId, long expectedDataVersion, int versionNo,
                                               ActivationRefusalType refusalType, String noteOrNull) {

    /** brokerage-agreement BR-17: the note stays out of every log line. */
    @Override
    public String toString() {
        return "AgreementRefuseActivationCommand[" + agreementId + ", " + versionNo + ", " + refusalType
                + ", dataVersion=" + expectedDataVersion + "]";
    }
}
