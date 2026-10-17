package vn.sunvibe.acquisition.core.domain.model.lead;

import vn.sunvibe.acquisition.core.domain.exception.InvalidIntakeValueException;
import vn.sunvibe.acquisition.core.domain.model.ValueObject;

import java.util.Objects;

/**
 * Price the owner hopes to get, in whole VND (acquisition-owner-intake BR-4). The unit must be the one the intent
 * needs: night for short-term rent, month for long-term rent, total for a sale.
 */
public final class AskingPrice extends ValueObject<AskingPrice> {

    public static final long MAX_AMOUNT = 999_999_999_999_999L;
    public static final String CURRENCY = "VND";

    private final long amount;
    private final PriceUnit unit;

    private AskingPrice(long amount, PriceUnit unit) {
        this.amount = amount;
        this.unit = unit;
    }

    /**
     * Check the amount (1 to {@value #MAX_AMOUNT}), the currency (VND) and that the unit fits the intent.
     */
    public static AskingPrice of(long amount, String currency, PriceUnit unit, OwnerLeadIntent intent) {
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new InvalidIntakeValueException("askingPrice.amount",
                    "Asking price amount must be between 1 and " + MAX_AMOUNT + ".");
        }
        if (!CURRENCY.equals(currency)) {
            throw new InvalidIntakeValueException("askingPrice.currency", "Asking price currency must be VND.");
        }
        if (unit == null || intent == null || unit != intent.requiredPriceUnit()) {
            throw new InvalidIntakeValueException("askingPrice.unit",
                    "Asking price unit does not match what the owner wants to do with the property.");
        }
        return new AskingPrice(amount, unit);
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return CURRENCY;
    }

    public PriceUnit getUnit() {
        return unit;
    }

    @Override
    protected boolean equalsCore(AskingPrice other) {
        return other != null && amount == other.amount && unit == other.unit;
    }

    @Override
    protected int hashCodeCore() {
        return Objects.hash(amount, unit);
    }

    @Override
    public String toString() {
        return amount + " " + CURRENCY + "/" + unit;
    }
}
