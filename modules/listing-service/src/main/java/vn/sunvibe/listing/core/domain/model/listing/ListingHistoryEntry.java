package vn.sunvibe.listing.core.domain.model.listing;

import java.time.Instant;

/**
 * One row of a listing's history (listing BR-3, LM-2: the event, its actor, time and revision).
 *
 * @param revisionNoOrNull the revision the event concerns, if any
 * @param actorIdOrNull    the user who acted; null for the system
 */
public record ListingHistoryEntry(String id, ListingHistoryAction action, Integer revisionNoOrNull,
                                  String actorIdOrNull, Instant occurredAt) {

    public ListingHistoryEntry {
        if (id == null || action == null || occurredAt == null) {
            throw new IllegalStateException("a listing history entry needs its id, action and time");
        }
    }
}
