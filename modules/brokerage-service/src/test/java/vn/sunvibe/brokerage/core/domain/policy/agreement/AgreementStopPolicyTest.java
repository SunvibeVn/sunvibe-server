package vn.sunvibe.brokerage.core.domain.policy.agreement;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.domain.model.agreement.AgreementStatus;
import vn.sunvibe.brokerage.core.domain.model.agreement.StopAction;
import vn.sunvibe.brokerage.core.domain.model.agreement.TerminationType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stop rule (brokerage-agreement BR-14 to BR-16, US-1 AC-5, US-5 AC-2; E05-S8 spec S8-D11's table, plan E05S8-P4):
 * which stop a viewer may take on an agreement in its effective state, in the order of {@link StopAction}.
 */
class AgreementStopPolicyTest {

    private static final Set<AgreementStatus> UNFINISHED = EnumSet.of(AgreementStatus.DRAFTING,
            AgreementStatus.AWAITING_CONFIRMATION, AgreementStatus.ACTIVE, AgreementStatus.SUSPENDED);

    /** S8-D11's state column, written out from the spec. */
    private static final Map<StopAction, Set<AgreementStatus>> STATES = Map.of(
            StopAction.CANCEL, EnumSet.of(AgreementStatus.DRAFTING, AgreementStatus.AWAITING_CONFIRMATION),
            StopAction.SUSPEND, EnumSet.of(AgreementStatus.ACTIVE),
            StopAction.RELEASE_SUSPENSION, EnumSet.of(AgreementStatus.SUSPENDED),
            StopAction.TERMINATE_OWNER_WITHDREW, UNFINISHED,
            StopAction.TERMINATE_UNIT_LET, UNFINISHED,
            StopAction.TERMINATE_SUNVIBE, UNFINISHED);

    /** S8-D11's viewer column, written out from the spec. */
    private static boolean viewer(StopAction action, boolean assignedSale, boolean currentAssignee, boolean manager) {
        return switch (action) {
            case CANCEL, TERMINATE_UNIT_LET -> assignedSale;
            case SUSPEND, TERMINATE_SUNVIBE -> manager;
            case RELEASE_SUSPENSION -> manager && !currentAssignee;
            case TERMINATE_OWNER_WITHDREW -> assignedSale || manager;
        };
    }

    @Test
    void allowedActions_everyStateAndViewer_theSpecTable() {
        for (AgreementStatus state : AgreementStatus.values()) {
            for (int flags = 0; flags < 8; flags++) {
                boolean assignedSale = (flags & 1) != 0;
                boolean currentAssignee = (flags & 2) != 0;
                boolean manager = (flags & 4) != 0;
                List<StopAction> expected = new ArrayList<>();
                for (StopAction action : StopAction.values()) {
                    if (STATES.get(action).contains(state) && viewer(action, assignedSale, currentAssignee, manager)) {
                        expected.add(action);
                    }
                }
                assertEquals(expected, AgreementStopPolicy.allowedActions(state, assignedSale, currentAssignee, manager),
                        state + " assignedSale=" + assignedSale + " currentAssignee=" + currentAssignee + " manager="
                                + manager);
            }
        }
    }

    @Test
    void allowedActions_finalStates_none() {
        for (AgreementStatus state : List.of(AgreementStatus.EXPIRED, AgreementStatus.TERMINATED,
                AgreementStatus.CANCELLED)) {
            assertEquals(List.of(), AgreementStopPolicy.allowedActions(state, true, true, true), state.name());
            for (StopAction action : StopAction.values()) {
                assertFalse(AgreementStopPolicy.admits(action, state), state + " " + action);
            }
        }
    }

    @Test
    void allowedActions_assignedSaleOnDraftingAndManagerOnActive_theSpecOrder() {
        assertEquals(List.of(StopAction.CANCEL, StopAction.TERMINATE_OWNER_WITHDREW, StopAction.TERMINATE_UNIT_LET),
                AgreementStopPolicy.allowedActions(AgreementStatus.DRAFTING, true, true, false));
        assertEquals(List.of(StopAction.SUSPEND, StopAction.TERMINATE_OWNER_WITHDREW, StopAction.TERMINATE_SUNVIBE),
                AgreementStopPolicy.allowedActions(AgreementStatus.ACTIVE, false, false, true));
        assertEquals(List.of(StopAction.TERMINATE_OWNER_WITHDREW, StopAction.TERMINATE_UNIT_LET,
                        StopAction.TERMINATE_SUNVIBE),
                AgreementStopPolicy.allowedActions(AgreementStatus.SUSPENDED, true, true, true));
    }

    @Test
    void mayAct_ownerWithdrew_assignedSaleOrManager() {
        assertTrue(AgreementStopPolicy.mayAct(StopAction.TERMINATE_OWNER_WITHDREW, true, true, false));
        assertTrue(AgreementStopPolicy.mayAct(StopAction.TERMINATE_OWNER_WITHDREW, false, false, true));
        assertTrue(AgreementStopPolicy.mayAct(StopAction.TERMINATE_OWNER_WITHDREW, false, true, true));
        assertFalse(AgreementStopPolicy.mayAct(StopAction.TERMINATE_OWNER_WITHDREW, false, true, false));
        assertFalse(AgreementStopPolicy.mayAct(StopAction.TERMINATE_OWNER_WITHDREW, false, false, false));
    }

    @Test
    void mayAct_release_managerNotCurrentAssignee() {
        assertTrue(AgreementStopPolicy.mayAct(StopAction.RELEASE_SUSPENSION, false, false, true));
        assertFalse(AgreementStopPolicy.mayAct(StopAction.RELEASE_SUSPENSION, false, true, true));
        assertFalse(AgreementStopPolicy.mayAct(StopAction.RELEASE_SUSPENSION, true, true, true));
        assertFalse(AgreementStopPolicy.mayAct(StopAction.RELEASE_SUSPENSION, false, false, false));
    }

    @Test
    void actionOf_eachTerminationType_itsAction() {
        assertEquals(StopAction.TERMINATE_OWNER_WITHDREW, AgreementStopPolicy.actionOf(TerminationType.OWNER_WITHDREW));
        assertEquals(StopAction.TERMINATE_UNIT_LET, AgreementStopPolicy.actionOf(TerminationType.UNIT_LET));
        assertEquals(StopAction.TERMINATE_SUNVIBE, AgreementStopPolicy.actionOf(TerminationType.SUNVIBE_TERMINATED));
    }
}
