package vn.sunvibe.property.core.domain.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object representing Money (monetary value).
 * Uses BigDecimal to avoid currency rounding errors.
 */
public class Money extends ValueObject<Money> implements Serializable, Comparable<Money> {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String CURRENCY_VND = "VND";

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be greater than or equal to 0");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }

        this.amount = amount.setScale(0, java.math.RoundingMode.HALF_UP);
        this.currency = currency.toUpperCase();
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    /**
     * Create Money from BigDecimal and currency.
     */
    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    /**
     * Create Money from long and currency (convenience method).
     */
    public static Money of(long amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    /**
     * Create VND Money (Vietnamese currency).
     */
    public static Money vnd(BigDecimal amount) {
        return new Money(amount, CURRENCY_VND);
    }

    public static Money vnd(long amount) {
        return new Money(BigDecimal.valueOf(amount), CURRENCY_VND);
    }

    /**
     * Add two Money values of the same currency.
     */
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    String.format("Cannot add %s and %s", this.currency, other.currency)
            );
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtract two Money values of the same currency.
     */
    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    String.format("Cannot subtract %s from %s", this.currency, other.currency)
            );
        }
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    /**
     * Multiply Money by a number.
     */
    public Money multiply(BigDecimal multiplier) {
        return new Money(this.amount.multiply(multiplier), this.currency);
    }

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }

    @Override
    public int compareTo(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare different currencies");
        }
        return this.amount.compareTo(other.amount);
    }

    @Override
    protected boolean equalsCore(Money other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return Objects.equals(amount, other.amount) &&
                Objects.equals(currency, other.currency);
    }

    @Override
    protected int hashCodeCore() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return String.format("%,d %s", amount.longValue(), currency);
    }
}
