package vn.sunvibe.auth.core.domain.repository.user;

import java.time.Instant;

/**
 * The stored columns of an account that decide whether a proof of ownership stands (property-create intake D5-10), read in
 * one native query and never from a managed entity, because a password check can leave one stale.
 *
 * @param signInLosses the trigger's count of losses of sign-in; the entity does not map it
 */
public record AccountStanding(String userStatus, Instant deletedAt, int signInLosses, Instant createdDate) {
}
