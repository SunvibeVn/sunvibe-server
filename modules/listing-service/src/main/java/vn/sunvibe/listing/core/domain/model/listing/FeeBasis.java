package vn.sunvibe.listing.core.domain.model.listing;

/**
 * How a mandatory fee is charged, E05's closed list (brokerage-agreement BR-8; E06-S2 spec E06S2-D6): an amount with
 * its charge unit, included in the rent, or at the provider's rate.
 */
public enum FeeBasis {
    AMOUNT,
    INCLUDED,
    PROVIDER_TARIFF
}
