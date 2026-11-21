package vn.sunvibe.listing.core.domain.model.listing;

import java.util.List;

/**
 * The checklist an approval confirms (listing-publish BR-7; E06-S4 spec E06S4-D6), version 1: BR-7 items 1, 3, 4, 5 and
 * 6, in that order. Item 2 (photos) joins with the photo slices and item 7 (earlier listings and takedowns) with E06-S9,
 * each by a new checklist version; a stored decision keeps the version it was made under.
 */
public enum ReviewCheck {
    /** BR-7 item 1: the text, features and terms match the unit. */
    CONTENT_MATCHES_UNIT,
    /** BR-7 item 3: the text, the fee names of the terms included, follows the listing rules (listing BR-10). */
    TEXT_FOLLOWS_RULES,
    /** BR-7 item 4: rent, deposit and every mandatory fee equal the agreement version; the system compares them. */
    TERMS_MATCH_AGREEMENT,
    /** BR-7 item 5: no sign of a fake listing or of content taken from elsewhere. */
    NO_FRAUD_SIGNS,
    /** BR-7 item 6: the agreement's end and the current advertising right were checked. */
    AGREEMENT_END_AND_RIGHT;

    /** The checklist version an approval confirms now. */
    public static final int CHECKLIST_VERSION = 1;

    /** The codes of checklist version 1, in checklist order. */
    public static final List<ReviewCheck> VERSION_1 = List.of(values());
}
