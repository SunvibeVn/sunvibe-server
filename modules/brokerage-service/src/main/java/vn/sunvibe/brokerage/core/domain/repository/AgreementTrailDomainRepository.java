package vn.sunvibe.brokerage.core.domain.repository;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementAccessType;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementEventType;

import java.time.Instant;
import java.util.Collection;

/**
 * The insert-only trail of the agreements: their history and their access log. Rows hold no free text and no value
 * (brokerage-agreement BR-17: the history records the event, the actor, the time and the version; the access log who
 * opened which agreement and when).
 */
public interface AgreementTrailDomainRepository {

    /** Adds one history row of the agreement. */
    void recordEvent(String agreementId, Integer versionNoOrNull, AgreementEventType type, String actorUserIdOrNull,
                     Instant at);

    /**
     * Adds one access-log row (brokerage-agreement BR-17). The one write path of every detail read of an agreement, now
     * and in later slices.
     */
    void recordAccess(String userId, String agreementId, AgreementAccessType action, Instant at);

    /**
     * Adds one access-log row per agreement, in one round trip (brokerage-agreement BR-17; E05-S5 spec S5-D1: the owner's
     * read answers several agreements at once). Nothing for an empty collection.
     */
    void recordAccesses(String userId, Collection<String> agreementIds, AgreementAccessType action, Instant at);
}
