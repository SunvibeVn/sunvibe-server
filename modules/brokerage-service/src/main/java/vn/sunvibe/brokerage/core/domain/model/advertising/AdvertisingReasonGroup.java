package vn.sunvibe.brokerage.core.domain.model.advertising;

/**
 * The groups of reasons why Sunvibe may not advertise a unit now under an agreement (brokerage-agreement BR-12, BR-13,
 * US-4 AC-2; listing-publish section 7; E05-S6 spec S6-D7). The declaration order is the order of an answer. At most one
 * agreement-level group, then the condition groups of E05-S1 on the version that counts.
 */
public enum AdvertisingReasonGroup {
    /** The unit has no agreement: a consumer's own "none"; the published reads leave such a pair out. */
    NO_AGREEMENT,
    /** The agreement is drafting or awaiting confirmation (brokerage-agreement BR-13). */
    NOT_IN_EFFECT,
    /** Off only because an activated version has not started yet (listing-publish section 7). */
    NOT_STARTED,
    /** The agreement is suspended (brokerage-agreement BR-13). */
    SUSPENDED,
    /** The agreement reached a final state, or the end of its end date has passed (BR-6, BR-13, US-5 AC-1). */
    ENDED,
    /** The version's intake basis is no longer an entry of the unit, or it is not usable (brokerage-agreement BR-2). */
    BASIS,
    /** A stop for the whole property (brokerage-agreement BR-3). */
    PROPERTY,
    /** A stop of this unit (brokerage-agreement BR-3). */
    UNIT,
    /** The owner no longer meets brokerage-agreement BR-4, or is no longer the declared owner. */
    OWNER
}
