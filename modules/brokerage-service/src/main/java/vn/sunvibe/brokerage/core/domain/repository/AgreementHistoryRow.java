package vn.sunvibe.brokerage.core.domain.repository;

import java.time.Instant;
import java.util.Objects;

/**
 * One stored row of an agreement's history, as stored (brokerage-agreement BR-17: the event, the actor, the time and
 * the version only). The event code is the stored text: a later version of the code may have written one the enum
 * does not hold.
 *
 * @param versionNoOrNull    the version the event is about; null for none
 * @param actorUserIdOrNull  who acted; null for a transition Sunvibe's jobs stored
 */
public record AgreementHistoryRow(String eventType, Integer versionNoOrNull, String actorUserIdOrNull,
                                  Instant occurredAt) {

    public AgreementHistoryRow {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
