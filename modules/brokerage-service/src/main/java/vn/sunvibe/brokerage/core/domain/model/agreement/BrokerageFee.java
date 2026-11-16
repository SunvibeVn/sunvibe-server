package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

/**
 * The brokerage fee policy of a version (brokerage-agreement BR-7; the API's {@code feePolicy}, E05-S4 spec S4-D3): who
 * pays and when are fixed (the owner, when a lease is signed); how the fee is set, the days it is due after the signing
 * (0 to 30, 0 = on the signing day), what a renewal with the same tenant costs, what is refunded when the lease ends
 * early and the protection days after the agreement ends (0 to 180). Every part may stay null in a draft; refusing to send
 * a version with a missing part is {@code VersionSendRules}'. No fee is computed here (BR-7: E05 computes nothing).
 * <p>
 * The request converter refuses a type outside its place first, with the allowed values; this check holds the same
 * invariant for every other caller.
 */
public record BrokerageFee(FeeFormula formulaOrNull, Integer dueDaysOrNull, FeeFormula renewalOrNull,
                           EarlyLeaseEndRefund earlyLeaseEndOrNull, Integer protectionDaysOrNull) {

    /** Nothing of the fee policy entered yet. */
    public static final BrokerageFee NONE = new BrokerageFee(null, null, null, null, null);

    private static final int MAX_DUE_DAYS = 30;
    private static final int MAX_PROTECTION_DAYS = 180;

    public BrokerageFee {
        // brokerage-agreement BR-7: the fee is a percentage, an amount or free of charge.
        if (formulaOrNull != null && !formulaOrNull.type().forFee()) {
            throw new AgreementContentException("formula.type");
        }
        if (dueDaysOrNull != null && (dueDaysOrNull < 0 || dueDaysOrNull > MAX_DUE_DAYS)) {
            throw new AgreementContentException("dueDays");
        }
        // brokerage-agreement BR-7: a renewal is not charged, or charged by a percentage or an amount.
        if (renewalOrNull != null && !renewalOrNull.type().forRenewal()) {
            throw new AgreementContentException("renewal.type");
        }
        if (protectionDaysOrNull != null && (protectionDaysOrNull < 0 || protectionDaysOrNull > MAX_PROTECTION_DAYS)) {
            throw new AgreementContentException("protectionDays");
        }
    }

    /** brokerage-agreement BR-7 and owner G2 D-23: the owner pays the fee. */
    public FeePayer payer() {
        return FeePayer.OWNER;
    }

    /** brokerage-agreement BR-7 and owner G2 D-23: the fee arises when a lease is signed. */
    public FeeChargePoint chargedWhen() {
        return FeeChargePoint.LEASE_SIGNED;
    }

    /** brokerage-agreement BR-17: the fee policy is private, so the parts' types and presence, never a value. */
    @Override
    public String toString() {
        return "BrokerageFee[formula=" + formulaOrNull + ", dueDays=" + present(dueDaysOrNull) + ", renewal="
                + renewalOrNull + ", earlyLeaseEnd=" + earlyLeaseEndOrNull + ", protectionDays="
                + present(protectionDaysOrNull) + "]";
    }

    private static String present(Object valueOrNull) {
        return valueOrNull == null ? "none" : "set";
    }
}
