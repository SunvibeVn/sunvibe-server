package vn.sunvibe.property.core.application.shared.external.user;

/** A masked address and the age of an account (property-create intake spec 4.6 step 6). */
public record OwnerAccountFacts(String addressMasked, long ageDays) {
}
