package vn.sunvibe.brokerage.core.application.command;

/**
 * The owner declines the version waiting for them, with an optional reason (brokerage-agreement US-2 AC-3; E05-S5 spec
 * S5-D7).
 *
 * @param reasonOrNull the reason as written; private (brokerage-agreement BR-17)
 */
public record AgreementRejectCommand(String agreementId, int versionNo, String reasonOrNull) {

    /** brokerage-agreement BR-17: the reason stays out of every log line. */
    @Override
    public String toString() {
        return "AgreementRejectCommand[" + agreementId + ", " + versionNo + "]";
    }
}
