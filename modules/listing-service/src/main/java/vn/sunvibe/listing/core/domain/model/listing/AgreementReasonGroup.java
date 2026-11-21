package vn.sunvibe.listing.core.domain.model.listing;

import java.util.Arrays;

/**
 * A reason group of E05's advertising answer for a unit (brokerage-agreement BR-12, BR-13; listing-publish BR-1: a
 * listing is refused with E05's reason group), in E05's order. E05 publishes the groups as codes; listing reads them
 * through {@link #ofCode}, which fails for a code it does not know, so an unknown code is never read as allowed.
 */
public enum AgreementReasonGroup {
    NO_AGREEMENT,
    NOT_IN_EFFECT,
    NOT_STARTED,
    SUSPENDED,
    ENDED,
    BASIS,
    PROPERTY,
    UNIT,
    OWNER;

    /**
     * The group of this exact code.
     *
     * @throws IllegalStateException for null or a code listing does not know; the message names no value
     */
    public static AgreementReasonGroup ofCode(String code) {
        return Arrays.stream(values())
                .filter(group -> group.name().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("an advertising reason group code listing does not know"));
    }
}
