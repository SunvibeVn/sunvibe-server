package vn.sunvibe.brokerage.core.domain.model.agreement;

import java.util.Arrays;
import java.util.Optional;

/** How the deposit of the confirmed terms is stated (brokerage-agreement BR-8): a number of months' rent or an amount. */
public enum DepositBasis {
    MONTHS,
    AMOUNT;

    /** The basis of this exact name; empty for null or any other text, a different case included. */
    public static Optional<DepositBasis> of(String codeOrNull) {
        return Arrays.stream(values()).filter(basis -> basis.name().equals(codeOrNull)).findFirst();
    }
}
