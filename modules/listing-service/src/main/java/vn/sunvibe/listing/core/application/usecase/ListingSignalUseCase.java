package vn.sunvibe.listing.core.application.usecase;

/**
 * Use Case - listing's answer to E05's event (listing-publish E06-S7 spec E06S7-D13; ADR-012 decision 6, ADR-014
 * decisions 6, 7): the per-unit advertising answer of a unit may have changed, so each listing of that unit that has a
 * publication row is signalled to be read again. Its one allowed caller is the outbox consumer adapter
 * {@code vn.sunvibe.integration.ListingUnitAdvertisingAdapter}; it is never exposed over HTTP, and an ArchUnit rule
 * holds that (E06S7-D6).
 */
public interface ListingSignalUseCase {

    /**
     * Appends the listing event, due now, for every listing of the unit that has a publication row, in id order, in one
     * transaction; a unit without one appends nothing. A repeated call appends repeated signals.
     */
    void signalUnit(String unitId);
}
