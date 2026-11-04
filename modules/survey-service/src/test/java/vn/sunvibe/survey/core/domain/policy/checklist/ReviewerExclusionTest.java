package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may not decide a revision (survey-checklist submission spec 4.2, D3-9, D3-14, S-5): one test per source of the
 * excluded set; the loader that fills the facts has its own PostgreSQL test.
 */
class ReviewerExclusionTest {

    private static final String PERFORMER = "01JPERF0RMER00000000000001";
    private static final String RECORDER = "01JREC0RDER000000000000001";
    private static final String CANDIDATE = "01JCAND1DATE00000000000001";
    private static final String ASSIGNEE = "01JASS1GNEE000000000000001";

    private static ReviewerExclusionFacts facts(Set<String> holders, Set<String> submitters, Set<String> savers,
                                                Set<String> uploaders, Set<String> removers,
                                                Set<String> deselectors) {
        return new ReviewerExclusionFacts(PERFORMER, RECORDER, holders, submitters, savers, uploaders, removers,
                deselectors);
    }

    private static ReviewerExclusionFacts none() {
        return facts(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    @Test
    void exclusion_visitPerformer_excluded() {
        assertTrue(ReviewerExclusion.excluded(none(), ASSIGNEE).contains(PERFORMER));
    }

    @Test
    void exclusion_visitRecorder_excluded() {
        assertTrue(ReviewerExclusion.excluded(none(), ASSIGNEE).contains(RECORDER));
    }

    @Test
    void exclusion_holder_excluded() {
        assertTrue(ReviewerExclusion.excluded(facts(Set.of(CANDIDATE), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of()), ASSIGNEE).contains(CANDIDATE));
    }

    @Test
    void exclusion_submitter_excluded() {
        assertTrue(ReviewerExclusion.excluded(facts(Set.of(), Set.of(CANDIDATE), Set.of(), Set.of(), Set.of(),
                Set.of()), ASSIGNEE).contains(CANDIDATE));
    }

    @Test
    void exclusion_saver_excluded() {
        assertTrue(ReviewerExclusion.excluded(facts(Set.of(), Set.of(), Set.of(CANDIDATE), Set.of(), Set.of(),
                Set.of()), ASSIGNEE).contains(CANDIDATE));
    }

    @Test
    void exclusion_uploader_excluded() {
        assertTrue(ReviewerExclusion.excluded(facts(Set.of(), Set.of(), Set.of(), Set.of(CANDIDATE), Set.of(),
                Set.of()), ASSIGNEE).contains(CANDIDATE));
    }

    @Test
    void exclusion_remover_excluded() {
        assertTrue(ReviewerExclusion.excluded(facts(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(CANDIDATE),
                Set.of()), ASSIGNEE).contains(CANDIDATE));
    }

    @Test
    void exclusion_deselector_excluded() {
        assertTrue(ReviewerExclusion.excluded(facts(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(CANDIDATE)), ASSIGNEE).contains(CANDIDATE));
    }

    /** survey-checklist D3-14: also someone who touched nothing, when the lead is theirs now. */
    @Test
    void exclusion_currentAssignee_excluded() {
        assertTrue(ReviewerExclusion.excluded(none(), CANDIDATE).contains(CANDIDATE));
    }

    /** A NEW lead has no assignee: nobody is excluded for that reason, and the set takes no null. */
    @Test
    void exclusion_noAssignee_addsNothing() {
        assertEquals(Set.of(PERFORMER, RECORDER), ReviewerExclusion.excluded(none(), null));
    }

    /** spec 4.2: hearing the owner's statement does not touch the record: a receiver stays free to decide. */
    @Test
    void exclusion_ownerStatementReceiver_notExcluded() {
        assertFalse(ReviewerExclusion.excluded(none(), ASSIGNEE).contains(CANDIDATE));
    }
}
