package vn.sunvibe.brokerage.core.application.command;

import vn.sunvibe.brokerage.core.domain.model.agreement.SuspensionReasonType;

/**
 * A manager suspends an agreement in effect (brokerage-agreement BR-15, US-5 AC-2; E05-S8 spec 3.1, S8-D6). No data
 * version: a stop decides on the state read under its locks (S8-D8's named narrowing).
 *
 * @param noteOrNull the optional note as sent; trimmed and checked by the domain
 */
public record AgreementSuspendCommand(String agreementId, SuspensionReasonType reasonType, String noteOrNull) {

    /** brokerage-agreement BR-17: the note stays out of every log line. */
    @Override
    public String toString() {
        return "AgreementSuspendCommand[" + agreementId + ", " + reasonType + "]";
    }
}
