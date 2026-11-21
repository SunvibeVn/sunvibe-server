package vn.sunvibe.listing.core.domain.policy.listing;

import vn.sunvibe.listing.core.domain.model.listing.RevisionContent;
import vn.sunvibe.listing.core.domain.model.listing.RevisionPart;
import vn.sunvibe.listing.core.domain.model.listing.RevisionRule;
import vn.sunvibe.listing.core.domain.model.listing.RevisionTerms;
import vn.sunvibe.listing.core.domain.model.listing.RevisionViolation;

import java.util.ArrayList;
import java.util.List;

/**
 * What a revision needs to be submitted for review (listing-publish US-2 AC-4, BR-2, BR-4, E-4; E06-S2 spec E06S2-D7):
 * a title of 15 to 90 characters, a description of 100 to 3,000 characters, a terms snapshot, and title and description
 * passing the machine check. Every violation once, in this order: the title's presence and length, the description's,
 * the terms, then the title's machine rules and the description's, each in {@link RevisionRule} order. Characters are
 * code points. The same list answers a refused submission and the problems of a Draft's read.
 */
public final class RevisionSubmitRules {

    /** listing-publish BR-2: a title of at least 15 characters. */
    public static final int TITLE_MIN = 15;
    /** listing-publish BR-2: a description of at least 100 characters. */
    public static final int DESCRIPTION_MIN = 100;

    private RevisionSubmitRules() {
    }

    public static List<RevisionViolation> violations(RevisionContent content, RevisionTerms termsOrNull) {
        List<RevisionViolation> violations = new ArrayList<>();
        length(violations, RevisionPart.TITLE, content.titleOrNull(), TITLE_MIN, RevisionContent.TITLE_MAX);
        length(violations, RevisionPart.DESCRIPTION, content.descriptionOrNull(), DESCRIPTION_MIN,
                RevisionContent.DESCRIPTION_MAX);
        // listing-publish BR-4: a revision is submitted with the terms snapshot it advertises
        if (termsOrNull == null) {
            violations.add(new RevisionViolation(RevisionPart.TERMS, RevisionRule.REQUIRED));
        }
        machine(violations, RevisionPart.TITLE, content.titleOrNull());
        machine(violations, RevisionPart.DESCRIPTION, content.descriptionOrNull());
        return List.copyOf(violations);
    }

    private static void length(List<RevisionViolation> violations, RevisionPart part, String textOrNull, int minimum,
                               int maximum) {
        if (textOrNull == null) {
            violations.add(new RevisionViolation(part, RevisionRule.REQUIRED));
            return;
        }
        int length = RevisionContent.codePoints(textOrNull);
        if (length < minimum) {
            violations.add(new RevisionViolation(part, RevisionRule.TOO_SHORT));
        } else if (length > maximum) {
            violations.add(new RevisionViolation(part, RevisionRule.TOO_LONG));
        }
    }

    private static void machine(List<RevisionViolation> violations, RevisionPart part, String textOrNull) {
        ListingTextRules.broken(textOrNull).forEach(rule -> violations.add(new RevisionViolation(part, rule)));
    }
}
