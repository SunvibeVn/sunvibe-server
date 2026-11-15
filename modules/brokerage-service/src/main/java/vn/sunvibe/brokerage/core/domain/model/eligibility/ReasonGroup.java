package vn.sunvibe.brokerage.core.domain.model.eligibility;

/**
 * The groups of reasons that stop an agreement on one unit (brokerage-agreement US-1 AC-1, BR-17: reasons are shown in
 * groups and never name another lead). The declaration order is the order of an answer.
 */
public enum ReasonGroup {
    /** The lead's intake result is not usable now, or it does not map the unit (brokerage-agreement BR-2). */
    BASIS,
    /** A stop for the whole property (brokerage-agreement BR-3 (a) to (e)). */
    PROPERTY,
    /** A stop of this unit (brokerage-agreement BR-3 (f), (g)). */
    UNIT,
    /** The owner on record does not meet brokerage-agreement BR-4. */
    OWNER
}
