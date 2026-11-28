package vn.sunvibe.listing.core.domain.model.listing;

/**
 * One rule a revision breaks (listing-publish US-2 AC-4, E-4; E06-S2 spec E06S2-D7): the part and the rule only, never
 * the text that broke it; for a fee name the fee's 1-based position in E05's order (E06-S7 spec E06S7-D10, E-20), set
 * exactly for {@link RevisionPart#FEE_NAME}.
 */
public record RevisionViolation(RevisionPart part, RevisionRule rule, Integer feePositionOrNull) {

    /** At most 20 mandatory fees (E05's rule, listing_revision_fees.fee_no). */
    public static final int MAX_FEE_POSITION = 20;

    public RevisionViolation {
        if (part == null || rule == null) {
            throw new IllegalArgumentException("a revision violation needs its part and rule");
        }
        if ((part == RevisionPart.FEE_NAME) != (feePositionOrNull != null)) {
            throw new IllegalArgumentException("a fee name violation, and only one, names the fee's position");
        }
        if (feePositionOrNull != null && (feePositionOrNull < 1 || feePositionOrNull > MAX_FEE_POSITION)) {
            throw new IllegalArgumentException("a fee's position is 1 to 20");
        }
    }

    /** A violation of the title, the description or the terms: no position. */
    public RevisionViolation(RevisionPart part, RevisionRule rule) {
        this(part, rule, null);
    }
}
