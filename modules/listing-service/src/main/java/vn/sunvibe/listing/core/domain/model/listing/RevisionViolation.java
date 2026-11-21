package vn.sunvibe.listing.core.domain.model.listing;

/**
 * One rule a revision breaks (listing-publish US-2 AC-4, E-4; E06-S2 spec E06S2-D7): the part and the rule only, never
 * the text that broke it.
 */
public record RevisionViolation(RevisionPart part, RevisionRule rule) {

    public RevisionViolation {
        if (part == null || rule == null) {
            throw new IllegalArgumentException("a revision violation needs its part and rule");
        }
    }
}
