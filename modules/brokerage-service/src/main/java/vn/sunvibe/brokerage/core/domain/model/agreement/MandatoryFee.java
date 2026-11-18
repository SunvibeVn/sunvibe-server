package vn.sunvibe.brokerage.core.domain.model.agreement;

import vn.sunvibe.brokerage.core.domain.exception.AgreementContentException;

/**
 * One mandatory fee of the confirmed terms besides the rent (brokerage-agreement BR-8): an amount in whole VND with its
 * charge unit, included in the rent, or at the provider's rate. The name is trimmed; the first failing rule refuses the
 * fee with the field path inside the fee.
 */
public record MandatoryFee(String name, FeeBasis basis, Long amountOrNull, ChargeUnit chargeUnitOrNull) {

    /** Technical input bound of a fee name, in UTF-16 units as Bean Validation counts them. */
    public static final int MAX_NAME_LENGTH = 100;

    public MandatoryFee {
        if (name == null || holdsControlCharacter(name)) {
            throw new AgreementContentException("name");
        }
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new AgreementContentException("name");
        }
        if (basis == null) {
            throw new AgreementContentException("basis");
        }
        if (basis == FeeBasis.AMOUNT) {
            // brokerage-agreement AC-3 and E-13: an amount of 0 never passes; "included in the rent" is its own basis.
            if (amountOrNull == null || amountOrNull < 1 || amountOrNull > VersionTerms.MAX_AMOUNT) {
                throw new AgreementContentException("amount");
            }
            if (chargeUnitOrNull == null) {
                throw new AgreementContentException("chargeUnit");
            }
        } else {
            if (amountOrNull != null) {
                throw new AgreementContentException("amount");
            }
            if (chargeUnitOrNull != null) {
                throw new AgreementContentException("chargeUnit");
            }
        }
    }

    /** U+0000 to U+001F anywhere in the name: PostgreSQL cannot store U+0000, and the others are no text of a name. */
    private static boolean holdsControlCharacter(String name) {
        return name.chars().anyMatch(c -> c < 0x20);
    }

    /** brokerage-agreement BR-17: the terms are private, so never the name or the amount. */
    @Override
    public String toString() {
        return "MandatoryFee[" + basis + "]";
    }
}
