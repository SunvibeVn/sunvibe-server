package vn.sunvibe.brokerage.core.application.command;

import vn.sunvibe.brokerage.core.domain.model.agreement.IntroducedBySunvibe;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;
import vn.sunvibe.brokerage.core.domain.model.agreement.WithdrawalChannel;

import java.time.LocalDate;

/**
 * Staff terminate an agreement that has not ended, for one of its three reasons with the fields that reason takes
 * (brokerage-agreement BR-14, BR-16, US-5 AC-2; E05-S8 spec 3.3, S8-D5). No data version: a stop decides on the state
 * read under its locks (S8-D8's named narrowing).
 *
 * @param noteOrNull the note as sent; trimmed and checked by the domain
 */
public record AgreementTerminateCommand(String agreementId, TerminationType type, LocalDate reportedOnOrNull,
                                        WithdrawalChannel channelOrNull, IntroducedBySunvibe introducedOrNull,
                                        String noteOrNull) {

    /** brokerage-agreement BR-17: the date, the channel, the answer on the tenant and the note stay out of every log. */
    @Override
    public String toString() {
        return "AgreementTerminateCommand[" + agreementId + ", " + type + "]";
    }
}
