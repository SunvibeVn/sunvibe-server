package vn.sunvibe.auth.core.domain.repository.user;

import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

/**
 * An account read under its row lock, in the transaction that holds the lock. It is the only way to update an account
 * (auth-email-access I-4): an aggregate loaded before the lock cannot be written back. Only the repository adapter
 * builds one.
 */
public record LockedUser(AppUser user) {
}
