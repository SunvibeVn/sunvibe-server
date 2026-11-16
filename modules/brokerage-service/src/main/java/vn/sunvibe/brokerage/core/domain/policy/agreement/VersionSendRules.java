package vn.sunvibe.brokerage.core.domain.policy.agreement;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementVersion;
import vn.sunvibe.brokerage.core.domain.model.agreement.BrokerageFee;
import vn.sunvibe.brokerage.core.domain.model.agreement.VersionTerms;

import java.util.ArrayList;
import java.util.List;

/**
 * What a version needs before it is sent to the owner (E05-S4 spec S4-D5). brokerage-agreement BR-8 and E-13: no part of
 * a version sent to the owner is unknown ("chưa biết thì chưa gửi"), and 0 is a stated value, so a deposit of 0, 0 due
 * days, 0 protection days and an empty fee list are present. BR-7: the fee policy is part of what the owner confirms. The
 * values themselves held their rules when they were saved; this check reads presence only.
 */
public final class VersionSendRules {

    private VersionSendRules() {
    }

    /**
     * The parts of the version that are not entered yet, as request paths in this order: {@code photoConsent},
     * {@code terms.monthlyRent}, {@code terms.deposit}, {@code terms.minimumTermMonths},
     * {@code terms.paymentPeriodMonths}, {@code terms.mandatoryFees}, {@code feePolicy.formula},
     * {@code feePolicy.dueDays}, {@code feePolicy.renewal}, {@code feePolicy.earlyLeaseEnd},
     * {@code feePolicy.protectionDays}. Empty when the version may be sent.
     */
    public static List<String> missingParts(AgreementVersion version) {
        List<String> missing = new ArrayList<>();
        // brokerage-agreement BR-9: the owner answers the photo consent yes or no; the exterior is always answered.
        if (version.getPhotoConsent().interiorOrNull() == null) {
            missing.add("photoConsent");
        }
        VersionTerms terms = version.getTerms();
        addIfNull(missing, terms.monthlyRentOrNull(), "terms.monthlyRent");
        addIfNull(missing, terms.depositOrNull(), "terms.deposit");
        addIfNull(missing, terms.minimumTermMonthsOrNull(), "terms.minimumTermMonths");
        addIfNull(missing, terms.paymentPeriodMonthsOrNull(), "terms.paymentPeriodMonths");
        addIfNull(missing, terms.mandatoryFeesOrNull(), "terms.mandatoryFees");
        BrokerageFee fee = version.getFee();
        addIfNull(missing, fee.formulaOrNull(), "feePolicy.formula");
        addIfNull(missing, fee.dueDaysOrNull(), "feePolicy.dueDays");
        addIfNull(missing, fee.renewalOrNull(), "feePolicy.renewal");
        addIfNull(missing, fee.earlyLeaseEndOrNull(), "feePolicy.earlyLeaseEnd");
        addIfNull(missing, fee.protectionDaysOrNull(), "feePolicy.protectionDays");
        return List.copyOf(missing);
    }

    private static void addIfNull(List<String> missing, Object valueOrNull, String part) {
        if (valueOrNull == null) {
            missing.add(part);
        }
    }
}
