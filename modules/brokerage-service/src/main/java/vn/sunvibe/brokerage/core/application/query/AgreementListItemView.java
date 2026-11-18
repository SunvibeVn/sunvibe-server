package vn.sunvibe.brokerage.core.application.query;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.TransactionType;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One row of the staff agreement list, as it stands at the read (E05-S7 spec S7-D3, S7-D5, section 4). No owner, lead,
 * intake, property or user id, no term, fee, contract number or reason (brokerage-agreement BR-17; E05-S6 spec S6-D21).
 *
 * @param status              the agreement's state at the read
 * @param latestVersionNo     the highest version number
 * @param latestVersionStatus that version's state at the read
 * @param advertisingAllowed  whether Sunvibe may advertise the unit now (brokerage-agreement BR-12)
 * @param reasonGroups        the codes of the reason groups, in their order; empty when allowed
 * @param endDateOrNull       the end date of the version that counts
 * @param endsAtOrNull        the start of the day after that end date in Vietnam
 * @param startDateOrNull     the start date of an activated version not in effect yet
 */
public record AgreementListItemView(String agreementId, String unitCodeOrNull, TransactionType transactionType,
                                    AgreementStatus status, int latestVersionNo, VersionStatus latestVersionStatus,
                                    Instant createdAt, boolean advertisingAllowed, List<String> reasonGroups,
                                    LocalDate endDateOrNull, Instant endsAtOrNull, LocalDate startDateOrNull) {

    public AgreementListItemView {
        reasonGroups = List.copyOf(reasonGroups);
    }
}
