package vn.sunvibe.brokerage.core.application.command;

/**
 * A manager who is not the basis lead's assignee lifts the suspension (brokerage-agreement BR-15, US-5 AC-5; E05-S8 spec
 * 3.2, S8-D6), with the data version they read (BR-5).
 *
 * @param reason the reason as sent; trimmed and checked by the domain
 */
public record AgreementReleaseSuspensionCommand(String agreementId, long expectedDataVersion, String reason) {

    /** brokerage-agreement BR-17: the reason stays out of every log line. */
    @Override
    public String toString() {
        return "AgreementReleaseSuspensionCommand[" + agreementId + ", " + expectedDataVersion + "]";
    }
}
