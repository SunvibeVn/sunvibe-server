package vn.sunvibe.brokerage.core.application.query;

import vn.sunvibe.brokerage.core.domain.model.agreement.AdvertisingScope;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.PhotoConsent;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The detail of a brokerage agreement for staff who may see its basis lead (E05-S2 spec 4; E05-S3 spec S3-D14). It holds
 * no owner, intake, property, creator or drafter id (brokerage-agreement BR-17: an answer carries only what the reader
 * needs).
 *
 * @param unitCodeOrNull       the unit's code as read from the basis at creation; null when the basis had none
 * @param basisLeadId          the basis lead of the latest version
 * @param versions             by version number
 * @param dataVersion          the agreement's row version: the data version a change sends back (BR-5)
 * @param viewerIsAssignedSale whether the viewer is the one who drafts on the basis lead (BR-16); a hint, the commands
 *                             decide
 */
public record AgreementDetailView(String agreementId, AgreementStatus status, TransactionType transactionType,
                                  String unitId, String unitCodeOrNull, String basisLeadId, Instant createdAt,
                                  List<VersionView> versions, long dataVersion, boolean viewerIsAssignedSale) {

    public AgreementDetailView {
        versions = List.copyOf(versions);
    }

    /**
     * One version: its number, state, calendar dates and content (brokerage-agreement BR-7 to BR-9), and when it was sent
     * to the owner (E05-S4 spec S4-D10).
     *
     * @param sentAtOrNull null for a version never sent
     */
    public record VersionView(int versionNo, VersionStatus status, LocalDate startDate, LocalDate endDate,
                              Instant createdAt, AdvertisingScope advertisingScope, PhotoConsent photoConsent,
                              VersionTerms terms, BrokerageFee fee, Instant sentAtOrNull) {
    }
}
