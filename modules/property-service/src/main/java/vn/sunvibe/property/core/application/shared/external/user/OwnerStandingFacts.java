package vn.sunvibe.property.core.application.shared.external.user;

/**
 * An account as a proof of ownership reads it (property-create intake D5-10).
 *
 * @param eligible     the account may sign in and holds no staff or admin role
 * @param signInLosses how often it lost sign-in; a proof is live only while the count it stored equals this one
 */
public record OwnerStandingFacts(boolean eligible, int signInLosses) {
}
