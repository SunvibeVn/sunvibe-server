package vn.sunvibe.listing.core.domain.exception;

import vn.sunvibe.listing.core.domain.model.listing.RevisionViolation;

import java.util.List;

/**
 * A Draft that breaks the submission rules (listing-publish US-2 AC-4, BR-2, BR-4, E-4; E06-S2 spec E06S2-D7): every
 * violation, in order, and never the text that broke a rule.
 */
public class RevisionNotSubmittableException extends RuntimeException {

    private final List<RevisionViolation> violations;

    public RevisionNotSubmittableException(List<RevisionViolation> violations) {
        super("The revision cannot be submitted.");
        this.violations = List.copyOf(violations);
    }

    public List<RevisionViolation> violations() {
        return violations;
    }
}
