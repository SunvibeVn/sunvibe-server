package vn.sunvibe.brokerage.core.domain.model.agreement;

/**
 * The stops a viewer may take on an agreement (E05-S8 spec S8-D11): the detail answers them in this declaration order
 * as a hint, and the commands decide with the same rule ({@code AgreementStopPolicy}).
 */
public enum StopAction {
    /** The assigned Sale cancels an agreement that never took effect (brokerage-agreement BR-5, BR-16, US-1 AC-5). */
    CANCEL,
    /** A manager suspends an agreement in effect (BR-15, BR-16). */
    SUSPEND,
    /** A manager who is not the basis lead's assignee lifts the suspension (BR-15, BR-16). */
    RELEASE_SUSPENSION,
    /** The assigned Sale or a manager records the owner's withdrawal (BR-14, BR-16). */
    TERMINATE_OWNER_WITHDREW,
    /** The assigned Sale records that the unit is let (BR-14, BR-16). */
    TERMINATE_UNIT_LET,
    /** A manager ends the agreement as Sunvibe's decision (BR-14, BR-16). */
    TERMINATE_SUNVIBE
}
