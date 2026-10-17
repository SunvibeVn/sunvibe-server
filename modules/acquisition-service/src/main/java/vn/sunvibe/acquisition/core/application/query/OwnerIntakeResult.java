package vn.sunvibe.acquisition.core.application.query;

/**
 * Result of an owner intake submission: the public request code of the lead, and whether the request repeated an
 * earlier one, so no new lead was created (acquisition-owner-intake BR-10).
 */
public record OwnerIntakeResult(String requestCode, boolean replayed) {
}
