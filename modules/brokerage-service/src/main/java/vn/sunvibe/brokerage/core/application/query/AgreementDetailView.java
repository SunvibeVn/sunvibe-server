package vn.sunvibe.brokerage.core.application.query;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The detail of a brokerage agreement for staff who may see its basis lead (E05-S2 spec 4). It holds no owner, intake,
 * property, creator or drafter id (brokerage-agreement BR-17: an answer carries only what the reader needs).
 *
 * @param unitCodeOrNull the unit's code as read from the basis at creation; null when the basis had none
 * @param basisLeadId    the basis lead of the latest version
 * @param versions       by version number
 */
public record AgreementDetailView(String agreementId, AgreementStatus status, TransactionType transactionType,
                                  String unitId, String unitCodeOrNull, String basisLeadId, Instant createdAt,
                                  List<VersionView> versions) {

    public AgreementDetailView {
        versions = List.copyOf(versions);
    }

    /** One version: its number, state and calendar dates. */
    public record VersionView(int versionNo, VersionStatus status, LocalDate startDate, LocalDate endDate,
                              Instant createdAt) {
    }
}
