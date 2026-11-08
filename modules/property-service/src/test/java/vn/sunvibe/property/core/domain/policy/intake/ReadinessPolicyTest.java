package vn.sunvibe.property.core.domain.policy.intake;

import org.junit.jupiter.api.Test;
import vn.sunvibe.property.core.domain.policy.intake.ReadinessPolicy.Facts;
import vn.sunvibe.property.core.domain.policy.intake.ReadinessPolicy.Result;
import vn.sunvibe.property.core.domain.model.lead.LeadStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.DUPLICATE;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.ON_HOLD;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.QUALIFIED;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.REJECTED;
import static vn.sunvibe.property.core.domain.model.lead.LeadStatus.WITHDRAWN;

/** The table of property-create intake spec 4.11. */
class ReadinessPolicyTest {

    private static final Facts FINE = new Facts(QUALIFIED, false, false, false, false, true, true, false);

    @Test
    void readiness_eachRow() {
        assertEquals(new Result(ReadinessState.USABLE, List.of()), ReadinessPolicy.of(FINE));
        assertSuspended(ReadinessReason.LEAD_ON_HOLD, new Facts(ON_HOLD, false, false, false, false, true, true, false));
        assertSuspended(ReadinessReason.EVIDENCE_HELD, new Facts(QUALIFIED, false, false, false, true, true, true, false));
        assertSuspended(ReadinessReason.OWNER_NOT_CONFIRMED, new Facts(QUALIFIED, false, false, false, false, false, true, false));
        assertSuspended(ReadinessReason.OWNER_NOT_ELIGIBLE, new Facts(QUALIFIED, false, false, false, false, true, false, false));
        assertSuspended(ReadinessReason.RESTRICTED, new Facts(QUALIFIED, false, false, false, false, true, true, true));
        for (LeadStatus closed : List.of(DUPLICATE, REJECTED, WITHDRAWN)) {
            assertEnded(ReadinessReason.valueOf(closed.name()),
                    new Facts(closed, false, false, false, false, true, true, false));
        }
        assertEnded(ReadinessReason.REQUALIFIED, new Facts(QUALIFIED, true, false, false, false, true, true, false));
        assertEnded(ReadinessReason.INTENT_CHANGED, new Facts(QUALIFIED, false, true, false, false, true, true, false));
        assertEnded(ReadinessReason.EVIDENCE_GONE, new Facts(QUALIFIED, false, false, true, false, true, true, false));
    }

    @Test
    void readiness_endedOverSuspended() {
        Result result = ReadinessPolicy.of(new Facts(ON_HOLD, true, false, false, true, false, false, true));

        assertEquals(ReadinessState.ENDED, result.state());
        assertEquals(List.of(ReadinessReason.REQUALIFIED), result.reasons());
    }

    @Test
    void readiness_severalSuspensions_allListed() {
        Result result = ReadinessPolicy.of(new Facts(ON_HOLD, false, false, false, true, false, true, true));

        assertEquals(ReadinessState.SUSPENDED, result.state());
        assertEquals(List.of(ReadinessReason.LEAD_ON_HOLD, ReadinessReason.EVIDENCE_HELD,
                ReadinessReason.OWNER_NOT_CONFIRMED, ReadinessReason.RESTRICTED), result.reasons());
    }

    /** spec 4.11: a whole-lead restriction suspends; the lead stays open. A loss of sign-in reads OWNER_NOT_CONFIRMED. */
    @Test
    void readiness_lostSignIn_notConfirmedBeforeNotEligible() {
        Result result = ReadinessPolicy.of(new Facts(QUALIFIED, false, false, false, false, false, false, false));

        assertEquals(List.of(ReadinessReason.OWNER_NOT_CONFIRMED, ReadinessReason.OWNER_NOT_ELIGIBLE), result.reasons());
    }

    @Test
    void missingForBrokerage_fixed() {
        assertEquals(List.of("AGREEMENT", "ADVERTISING_PERMISSION", "CONFIRMED_TERMS", "PHOTO_RIGHTS"),
                ReadinessPolicy.MISSING_FOR_BROKERAGE);
    }

    private static void assertSuspended(ReadinessReason reason, Facts facts) {
        assertEquals(new Result(ReadinessState.SUSPENDED, List.of(reason)), ReadinessPolicy.of(facts));
    }

    private static void assertEnded(ReadinessReason reason, Facts facts) {
        assertEquals(new Result(ReadinessState.ENDED, List.of(reason)), ReadinessPolicy.of(facts));
    }
}
