package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

import java.time.LocalDate;

/**
 * The signed document an activation records, as written on it (brokerage-agreement BR-11; E05-S6 spec S6-D3): its
 * number, the date it was signed and the name and title of Sunvibe's signatory. Sunvibe keeps no copy of the document
 * itself. Private (brokerage-agreement BR-17): no value appears in a text of this record.
 */
public record SignedDocument(String number, LocalDate signedOn, String sunvibeSignatoryName,
                             String sunvibeSignatoryTitle) {

    /** Technical bound of the document number, in UTF-16 units as Bean Validation and the UI count them. */
    public static final int MAX_NUMBER_LENGTH = 50;
    /** Technical bound of the signatory's name and title, in UTF-16 units. */
    public static final int MAX_SIGNATORY_LENGTH = 100;
    /** The earliest signing date accepted, against date arithmetic on absurd values. */
    public static final LocalDate EARLIEST_SIGNED_ON = AgreementPeriod.EARLIEST;

    /**
     * The document as entered, each text trimmed (E05-S6 spec S6-D3): the number 1 to 50 units, the name and title 1 to
     * 100 units, none holding a character that can hide the text (the rule of E05-S4 spec S4-D3); the signing date from
     * 2000-01-01 to {@code todayInVietnam}, since a document is not signed in the future. The first failing field
     * refuses it.
     *
     * @throws AgreementContentException on {@code documentNumber}, {@code signedOn}, {@code sunvibeSignatoryName} or
     *                                   {@code sunvibeSignatoryTitle}
     */
    public static SignedDocument of(String number, LocalDate signedOn, String sunvibeSignatoryName,
                                    String sunvibeSignatoryTitle, LocalDate todayInVietnam) {
        String trimmedNumber = text(number, MAX_NUMBER_LENGTH, "documentNumber");
        if (signedOn == null || signedOn.isBefore(EARLIEST_SIGNED_ON) || signedOn.isAfter(todayInVietnam)) {
            throw new AgreementContentException("signedOn");
        }
        String name = text(sunvibeSignatoryName, MAX_SIGNATORY_LENGTH, "sunvibeSignatoryName");
        String title = text(sunvibeSignatoryTitle, MAX_SIGNATORY_LENGTH, "sunvibeSignatoryTitle");
        return new SignedDocument(trimmedNumber, signedOn, name, title);
    }

    private static String text(String valueOrNull, int maxLength, String field) {
        if (valueOrNull == null) {
            throw new AgreementContentException(field);
        }
        String value = valueOrNull.strip();
        if (value.isEmpty() || value.length() > maxLength || AgreementText.hidesText(value)) {
            throw new AgreementContentException(field);
        }
        return value;
    }

    /** brokerage-agreement BR-17: the document's number, date and signatory stay out of every log line. */
    @Override
    public String toString() {
        return "SignedDocument[hidden]";
    }
}
