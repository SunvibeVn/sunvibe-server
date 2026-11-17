package vn.sunvibe.brokerage.core.application.query;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;

import java.util.List;

/**
 * One agreement as its owner reads it (brokerage-agreement US-2 AC-1; E05-S5 spec S5-D1, 3.1): the unit's code and real
 * location, the type, every version sent to them in full, and the statement of the version they may answer now. No
 * owner, lead, intake, property or creator id and no staff name (BR-17).
 *
 * @param status          the effective state (spec S5-D8)
 * @param versions        the versions with a sending time, by number
 * @param statementOrNull the statement of the agreement's version waiting for the owner while it may be answered
 */
public record OwnerAgreementView(String agreementId, AgreementStatus status, TransactionType transactionType,
                                 String unitCodeOrNull, String unitLocationOrNull, String streetOrNull,
                                 String wardNameOrNull, List<AgreementDetailView.VersionView> versions,
                                 OwnerAgreementsView.StatementView statementOrNull) {

    public OwnerAgreementView {
        versions = List.copyOf(versions);
    }

    /** brokerage-agreement BR-17: the location is the owner's private data, kept out of every log line. */
    @Override
    public String toString() {
        return "OwnerAgreementView[" + agreementId + ", " + status + "]";
    }
}
