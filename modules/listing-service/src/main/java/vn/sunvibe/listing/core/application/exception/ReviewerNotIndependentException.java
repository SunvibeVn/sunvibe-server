package vn.sunvibe.listing.core.application.exception;

import vn.sunvibe.listing.core.domain.model.listing.ReviewIneligibility;

import java.util.List;

/**
 * The caller may not decide the revision, or open its real location, now (listing-publish BR-7, US-4 AC-4; listing
 * BR-16; E06-S4 spec E06S4-D5 step 8, plan E06S4-P6): the reasons in contract order. Behind the controller's role gate
 * they are {@code AUTHOR} and {@code ASSIGNED_SALE}; a direct call without the role holds {@code NOT_A_REVIEWER} too. Its
 * message names no person or value.
 */
public final class ReviewerNotIndependentException extends RuntimeException {

    private final List<ReviewIneligibility> reasons;

    public ReviewerNotIndependentException(List<ReviewIneligibility> reasons) {
        super("The caller may not decide this revision.");
        this.reasons = List.copyOf(reasons);
    }

    public List<ReviewIneligibility> reasons() {
        return reasons;
    }
}
