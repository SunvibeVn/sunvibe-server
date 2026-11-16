package vn.sunvibe.brokerage.core.application.command;

import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Replace the whole content of an agreement's draft (brokerage-agreement US-1 AC-3, AC-5; E05-S3 spec S3-D1, S3-D5)
 * against the data version the caller read. The photo consent and the terms arrive built, so their rules have already
 * held; the transaction type stays a text, refused with the allowed values as in the create (E05-S2 spec S2-D5).
 */
public record AgreementDraftCommand(String agreementId, Long expectedDataVersion, String transactionType,
                                    LocalDate startDate, LocalDate endDate, PhotoConsent photoConsent,
                                    VersionTerms terms) {

    public AgreementDraftCommand {
        Objects.requireNonNull(agreementId, "agreementId");
        Objects.requireNonNull(expectedDataVersion, "expectedDataVersion");
        Objects.requireNonNull(transactionType, "transactionType");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(photoConsent, "photoConsent");
        Objects.requireNonNull(terms, "terms");
    }
}
