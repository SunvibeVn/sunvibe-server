package vn.sunvibe.listing.core.application.shared.external.brokerage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Port - E05's viewer-less advertising read, as listing uses it (listing-publish E06-S1 spec E06S1-D9, section 5.2;
 * ADR-009; brokerage-agreement as-built S6-D7 to S6-D11). The caller has checked first that its own caller may see what
 * it asks (ADR-012 decision 2): the read itself checks nothing. Every type here is a {@code java.*} value or a record of
 * this package.
 */
public interface AdvertisingRightPort {

    /** Group (ii) of each of those agreements; an unknown agreement is left out. */
    List<AgreementStandingFacts> standings(Collection<String> agreementIds);

    /**
     * E05's answer for the pair, with no lock; empty when the agreement does not exist or belongs to another unit (read
     * as "not allowed").
     */
    Optional<AdvertisingAnswerFacts> answer(String unitId, String agreementId);

    /**
     * E05's answer for the pair, read after a FOR SHARE lock on the agreement row held to the end of the caller's
     * transaction (ADR-012 decision 2 "Commands"): a stop that starts while it is held waits until the caller commits.
     * Lock order: the listing's row, then the agreement row. Empty when the agreement does not exist or belongs to
     * another unit.
     *
     * @throws IllegalStateException when called with no read-write transaction (PostgreSQL refuses the lock in a
     *                               read-only one)
     */
    Optional<AdvertisingAnswerFacts> answerUnderShareLock(String unitId, String agreementId);
}
