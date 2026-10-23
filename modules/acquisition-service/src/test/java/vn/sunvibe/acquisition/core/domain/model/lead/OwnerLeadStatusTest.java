package vn.sunvibe.acquisition.core.domain.model.lead;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Open and closed groups of the owner lead status (rebuild doc 09); transition pairs are tested with the queue.
 */
class OwnerLeadStatusTest {

    @Test
    void values_allStates_areTwelve() {
        assertEquals(12, OwnerLeadStatus.values().length);
    }

    @Test
    void isOpen_isTrueExactlyForNewAssignedContactedOnHoldSurveyScheduled() {
        assertEquals(EnumSet.of(OwnerLeadStatus.NEW, OwnerLeadStatus.ASSIGNED, OwnerLeadStatus.CONTACTED,
                OwnerLeadStatus.ON_HOLD, OwnerLeadStatus.SURVEY_SCHEDULED), statesWhere(OwnerLeadStatus::isOpen));
    }

    /** survey-schedule BR-3: a lead with survey work still takes notes, reassignments and duplicate checks. */
    @Test
    void surveyScheduled_isOpen() {
        assertTrue(OwnerLeadStatus.SURVEY_SCHEDULED.isOpen());
        assertFalse(OwnerLeadStatus.SURVEY_SCHEDULED.isClosed());
    }

    @Test
    void isClosed_isTrueExactlyForDuplicateRejectedWithdrawn() {
        assertEquals(EnumSet.of(OwnerLeadStatus.DUPLICATE, OwnerLeadStatus.REJECTED, OwnerLeadStatus.WITHDRAWN),
                statesWhere(OwnerLeadStatus::isClosed));
    }

    private static Set<OwnerLeadStatus> statesWhere(java.util.function.Predicate<OwnerLeadStatus> test) {
        return Arrays.stream(OwnerLeadStatus.values()).filter(test)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(OwnerLeadStatus.class)));
    }
}
