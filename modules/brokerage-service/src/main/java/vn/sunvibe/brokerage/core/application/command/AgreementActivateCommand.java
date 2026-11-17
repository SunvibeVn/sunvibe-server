package vn.sunvibe.brokerage.core.application.command;

import java.time.LocalDate;

/**
 * An independent manager activates the version the owner confirmed, against the signed document and the activation
 * statement (brokerage-agreement US-3 AC-1, AC-2, BR-11; E05-S6 spec S6-D1, S6-D3). The values are as sent: the service
 * builds the signed document with its own clock reading, so its 400 comes before any read (plan E05S6-P4).
 *
 * @param statementVersion      the version of the activation statement the manager made
 * @param documentNumber        the signed document's number; private (brokerage-agreement BR-17)
 * @param signedOn              its signing date, a calendar date in Vietnam
 * @param sunvibeSignatoryName  Sunvibe's signatory's name as written on it; private
 * @param sunvibeSignatoryTitle Sunvibe's signatory's title as written on it; private
 */
public record AgreementActivateCommand(String agreementId, long expectedDataVersion, int versionNo,
                                       String statementVersion, String documentNumber, LocalDate signedOn,
                                       String sunvibeSignatoryName, String sunvibeSignatoryTitle) {

    /** brokerage-agreement BR-17: the document's values stay out of every log line. */
    @Override
    public String toString() {
        return "AgreementActivateCommand[" + agreementId + ", " + versionNo + ", dataVersion=" + expectedDataVersion
                + "]";
    }
}
