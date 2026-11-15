package vn.sunvibe.brokerage.core.domain.policy.eligibility;

import org.junit.jupiter.api.Test;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy.Basis;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy.BasisUnit;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy.Holding;
import vn.sunvibe.brokerage.core.domain.policy.eligibility.EligibilityPolicy.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup.BASIS;
import static vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup.OWNER;
import static vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup.PROPERTY;
import static vn.sunvibe.brokerage.core.domain.model.eligibility.ReasonGroup.UNIT;

/**
 * The reason groups of one unit's agreement eligibility (brokerage-agreement US-1 AC-1, AC-2; BR-2, BR-3, BR-4, BR-16;
 * E05-S1 spec section 3.2, decisions S1-D3, S1-D4, S1-D5).
 */
class EligibilityPolicyTest {

    private static final String OWNER_ID = "U-OWNER";
    private static final Owner OK = new Owner(OWNER_ID, List.of(new Holding(OWNER_ID, "OWNER", "UNVERIFIED")), true);

    @Test
    void reasonGroups_usableBasisNoReasonOwnerPasses_none() {
        assertEquals(List.of(), EligibilityPolicy.reasonGroups("U1", usable(unit("U1")), OK));
    }

    @Test
    void reasonGroups_basisNotUsable_basis() {
        for (String state : Arrays.asList("SUSPENDED", "ENDED", "SOMETHING", null)) {
            Basis basis = new Basis(state, List.of(unit("U1")));

            assertEquals(List.of(BASIS), EligibilityPolicy.reasonGroups("U1", basis, OK), String.valueOf(state));
        }
    }

    @Test
    void reasonGroups_unitNotMapped_basis() {
        assertEquals(List.of(BASIS), EligibilityPolicy.reasonGroups("U9", usable(unit("U1")), OK));
    }

    @Test
    void reasonGroups_propertyStop_property() {
        assertEquals(List.of(PROPERTY), EligibilityPolicy.reasonGroups("U1", usable(unit("U1", "PROPERTY_STOP")), OK));
    }

    @Test
    void reasonGroups_restrictedOrUnitStop_unit() {
        assertEquals(List.of(UNIT), EligibilityPolicy.reasonGroups("U1", usable(unit("U1", "RESTRICTED")), OK));
        assertEquals(List.of(UNIT), EligibilityPolicy.reasonGroups("U1", usable(unit("U1", "UNIT_STOP")), OK));
        assertEquals(List.of(UNIT),
                EligibilityPolicy.reasonGroups("U1", usable(unit("U1", "RESTRICTED", "UNIT_STOP")), OK));
    }

    @Test
    void reasonGroups_unknownUnitReason_unit() {
        assertEquals(List.of(UNIT), EligibilityPolicy.reasonGroups("U1", usable(unit("U1", "NEW_REASON")), OK));
    }

    @Test
    void reasonGroups_availabilityDiffersOnly_none() {
        // brokerage-agreement BR-3 "Không chặn", D9-18: availability stops nothing in E05.
        assertEquals(List.of(),
                EligibilityPolicy.reasonGroups("U1", usable(unit("U1", "AVAILABILITY_DIFFERS")), OK));
    }

    @Test
    void reasonGroups_ownerChecks_owner() {
        Holding ownersRow = new Holding(OWNER_ID, "OWNER", "UNVERIFIED");
        List<Owner> failing = List.of(
                new Owner(null, List.of(ownersRow), true),
                new Owner(OWNER_ID, List.of(), true),
                new Owner(OWNER_ID, List.of(new Holding(OWNER_ID, "CO_OWNER", "UNVERIFIED")), true),
                new Owner(OWNER_ID, List.of(new Holding(OWNER_ID, "AUTHORIZED_REPRESENTATIVE", "UNVERIFIED")), true),
                new Owner(OWNER_ID, List.of(ownersRow, new Holding("U-OTHER", "OWNER", "UNVERIFIED")), true),
                new Owner(OWNER_ID, List.of(new Holding(OWNER_ID, "OWNER", "REJECTED")), true),
                new Owner(OWNER_ID, List.of(new Holding(OWNER_ID, "SUBLESSOR", "UNVERIFIED")), true),
                new Owner(OWNER_ID, List.of(new Holding(OWNER_ID, "OWNER", "PENDING")), true),
                new Owner(OWNER_ID, List.of(ownersRow), false));

        for (Owner owner : failing) {
            assertEquals(List.of(OWNER), EligibilityPolicy.reasonGroups("U1", usable(unit("U1")), owner),
                    owner.toString());
        }
    }

    @Test
    void reasonGroups_ownerUnverifiedOrVerified_passes() {
        Holding unverified = new Holding(OWNER_ID, "OWNER", "UNVERIFIED");
        Holding verified = new Holding(OWNER_ID, "OWNER", "VERIFIED");
        for (List<Holding> holdings : List.of(List.of(unverified), List.of(verified), List.of(unverified, verified))) {
            Owner owner = new Owner(OWNER_ID, holdings, true);

            assertEquals(List.of(), EligibilityPolicy.reasonGroups("U1", usable(unit("U1")), owner),
                    holdings.toString());
        }
    }

    @Test
    void reasonGroups_everyGroup_inDeclarationOrderOnce() {
        Basis basis = new Basis("SUSPENDED",
                List.of(unit("U1", "UNIT_STOP", "PROPERTY_STOP", "RESTRICTED", "NEW_REASON")));
        Owner failing = new Owner(OWNER_ID, List.of(), true);

        assertEquals(List.of(BASIS, PROPERTY, UNIT, OWNER), EligibilityPolicy.reasonGroups("U1", basis, failing));
    }

    @Test
    void assignedSale_assigneeWithSaleRole_true() {
        assertTrue(EligibilityPolicy.assignedSale("S", "S", Set.of("ROLE_SALE")));
        assertTrue(EligibilityPolicy.assignedSale("S", "S", Set.of("ROLE_SALE_LEADER", "ROLE_SALES_MANAGER")));
    }

    @Test
    void assignedSale_otherwise_false() {
        assertFalse(EligibilityPolicy.assignedSale(null, "S", Set.of("ROLE_SALE")));
        assertFalse(EligibilityPolicy.assignedSale("S", "M", Set.of("ROLE_SALE")));
        assertFalse(EligibilityPolicy.assignedSale("S", "S", Set.of("ROLE_SALES_MANAGER", "ROLE_ADMIN")));
    }

    private static Basis usable(BasisUnit... units) {
        return new Basis("USABLE", List.of(units));
    }

    private static BasisUnit unit(String unitId, String... reasons) {
        return new BasisUnit(unitId, List.of(reasons));
    }
}
