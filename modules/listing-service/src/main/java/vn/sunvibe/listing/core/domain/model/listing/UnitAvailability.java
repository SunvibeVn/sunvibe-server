package vn.sunvibe.listing.core.domain.model.listing;

/**
 * A unit's availability as property stores it, as the review read shows it (listing-publish E06-S4 spec E06S4-D11, plan
 * E06S4-P7). A value property sends that listing does not know is a broken contract, never shown as a known one.
 */
public enum UnitAvailability {
    AVAILABLE,
    OCCUPIED,
    MAINTENANCE,
    BLOCKED;

    /**
     * The availability of property's code.
     *
     * @throws IllegalStateException for any other code; its message names no value
     */
    public static UnitAvailability ofCode(String code) {
        return switch (code == null ? "" : code) {
            case "AVAILABLE" -> AVAILABLE;
            case "OCCUPIED" -> OCCUPIED;
            case "MAINTENANCE" -> MAINTENANCE;
            case "BLOCKED" -> BLOCKED;
            default -> throw new IllegalStateException("property sent an availability listing does not know");
        };
    }
}
