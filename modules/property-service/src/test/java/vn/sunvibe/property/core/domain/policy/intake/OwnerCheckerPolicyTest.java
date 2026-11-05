package vn.sunvibe.property.core.domain.policy.intake;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.sunvibe.property.core.domain.policy.intake.OwnerCheckerPolicy.Work;

/** property-create intake spec 4.3, D5-7: each kind of work rules a checker out alone. */
class OwnerCheckerPolicyTest {

    private static final String ME = "me";

    @Test
    void hasWorked_eachKindAlone() {
        assertTrue(OwnerCheckerPolicy.hasWorked(ME, new Work(ME, List.of(), null, null, Set.of())));
        assertTrue(OwnerCheckerPolicy.hasWorked(ME, new Work("a", List.of(ME), null, null, Set.of())));
        assertTrue(OwnerCheckerPolicy.hasWorked(ME, new Work("a", List.of(), ME, null, Set.of())));
        assertTrue(OwnerCheckerPolicy.hasWorked(ME, new Work("a", List.of(), null, ME, Set.of())));
        assertTrue(OwnerCheckerPolicy.hasWorked(ME, new Work("a", List.of(), null, null, Set.of(ME))));
    }

    @Test
    void hasWorked_noneOfThem_false() {
        assertFalse(OwnerCheckerPolicy.hasWorked(ME, new Work(null, List.of("a"), "b", "c", Set.of("d"))));
    }
}
