package vn.sunvibe.brokerage.core.domain.policy.agreement;

import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.StopAction;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;

import java.util.Arrays;
import java.util.List;

/**
 * Which stop a viewer may take on an agreement (E05-S8 spec S8-D11's table, plan E05S8-P4): one pure rule for the
 * detail's hint and for the commands, so the two cannot drift. The viewer column ({@link #mayAct}) and the state column
 * ({@link #admits}) are separate, so a command answers its permission and its state refusals in their own steps.
 */
public final class AgreementStopPolicy {

    private AgreementStopPolicy() {
    }

    /**
     * The viewer column, on the basis lead read now.
     *
     * @param viewerIsAssignedSale    the lead's current assignee holding SALE or SALE_LEADER
     * @param viewerIsCurrentAssignee the lead's current assignee, whatever role they hold
     * @param viewerIsManager         a viewer holding SALE_LEADER, SALES_MANAGER or ADMIN
     */
    public static boolean mayAct(StopAction action, boolean viewerIsAssignedSale, boolean viewerIsCurrentAssignee,
                                 boolean viewerIsManager) {
        return switch (action) {
            // brokerage-agreement BR-16: drafting, sending and cancelling an agreement that never took effect, and
            // recording that the unit is let, are the assignee's
            case CANCEL, TERMINATE_UNIT_LET -> viewerIsAssignedSale;
            // brokerage-agreement BR-15, BR-16: suspension and Sunvibe's termination are a manager's
            case SUSPEND, TERMINATE_SUNVIBE -> viewerIsManager;
            // brokerage-agreement BR-15: only a manager who is not the lead's assignee lifts a suspension
            case RELEASE_SUSPENSION -> viewerIsManager && !viewerIsCurrentAssignee;
            // brokerage-agreement BR-14, BR-16: the assignee or a manager records the owner's withdrawal
            case TERMINATE_OWNER_WITHDREW -> viewerIsAssignedSale || viewerIsManager;
        };
    }

    /** The state column, on the agreement's effective state. */
    public static boolean admits(StopAction action, AgreementStatus effective) {
        return switch (action) {
            // brokerage-agreement BR-5: cancelled means it never took effect
            case CANCEL -> effective == AgreementStatus.DRAFTING || effective == AgreementStatus.AWAITING_CONFIRMATION;
            // brokerage-agreement BR-15: a manager suspends an agreement in effect
            case SUSPEND -> effective == AgreementStatus.ACTIVE;
            case RELEASE_SUSPENSION -> effective == AgreementStatus.SUSPENDED;
            // brokerage-agreement BR-14 (E05-S8 spec S8-D1): termination acts on every agreement that has not ended
            case TERMINATE_OWNER_WITHDREW, TERMINATE_UNIT_LET, TERMINATE_SUNVIBE -> effective.isUnfinished();
        };
    }

    /** The actions of both columns, in the declaration order of {@link StopAction}; none on an ended agreement. */
    public static List<StopAction> allowedActions(AgreementStatus effective, boolean viewerIsAssignedSale,
                                                  boolean viewerIsCurrentAssignee, boolean viewerIsManager) {
        return Arrays.stream(StopAction.values())
                .filter(action -> admits(action, effective)
                        && mayAct(action, viewerIsAssignedSale, viewerIsCurrentAssignee, viewerIsManager))
                .toList();
    }

    /** The stop a termination of that type is. */
    public static StopAction actionOf(TerminationType type) {
        return switch (type) {
            case OWNER_WITHDREW -> StopAction.TERMINATE_OWNER_WITHDREW;
            case UNIT_LET -> StopAction.TERMINATE_UNIT_LET;
            case SUNVIBE_TERMINATED -> StopAction.TERMINATE_SUNVIBE;
        };
    }
}
