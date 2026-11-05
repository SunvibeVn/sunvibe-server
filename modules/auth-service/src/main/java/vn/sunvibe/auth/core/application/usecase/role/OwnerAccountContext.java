package vn.sunvibe.auth.core.application.usecase.role;

/**
 * What a manager reads about an account before an owner check (property-create intake spec 4.6 step 6): a masked address
 * and the account's age. A look-alike address reads the same, which is why the check does not rest on it (D5-7).
 */
public record OwnerAccountContext(String addressMasked, long ageDays) {
}
