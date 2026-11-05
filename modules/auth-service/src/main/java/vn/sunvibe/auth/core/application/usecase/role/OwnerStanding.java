package vn.sunvibe.auth.core.application.usecase.role;

/**
 * An account as a proof of ownership reads it (property-create intake D5-10). {@code signInLosses} counts every loss of
 * sign-in, so a proof stays live only while the count it stored is the current one; it is -1 for an account with no row.
 *
 * @param eligible the account may sign in and holds no staff or admin role
 */
public record OwnerStanding(boolean eligible, int signInLosses) {
}
