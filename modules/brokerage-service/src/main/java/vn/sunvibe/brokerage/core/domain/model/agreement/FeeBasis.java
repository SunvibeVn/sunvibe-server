package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/**
 * How a mandatory fee is charged (brokerage-agreement BR-8): an amount with its unit, included in the rent, or at the
 * provider's rate. Survey's "not offered" is no mandatory fee and has no basis here.
 */
public enum FeeBasis {
    AMOUNT,
    INCLUDED,
    PROVIDER_TARIFF;

    /** The basis of this exact name; empty for null or any other text, a different case included. */
    public static Optional<FeeBasis> of(String codeOrNull) {
        return Arrays.stream(values()).filter(basis -> basis.name().equals(codeOrNull)).findFirst();
    }
}
