package vn.sunvibe.survey.core.domain.policy.checklist;

import org.junit.jupiter.api.Test;
import vn.sunvibe.survey.core.domain.model.checklist.Hazard;
import vn.sunvibe.survey.core.domain.model.checklist.HazardLink;
import vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity;
import vn.sunvibe.survey.core.domain.model.checklist.HazardStatus;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity.IMPORTANT;
import static vn.sunvibe.survey.core.domain.model.checklist.HazardSeverity.MINOR;
import static vn.sunvibe.survey.core.domain.model.checklist.HazardStatus.OBSERVED_RESOLVED;
import static vn.sunvibe.survey.core.domain.model.checklist.HazardStatus.OPEN;
import static vn.sunvibe.survey.core.domain.model.checklist.HazardStatus.OWNER_REPORTED_FIXED;

/**
 * Earlier risks through the whole chain of checklists of one intent cycle (survey-checklist BR-24, spec A-20; W16-43,
 * W16-39), recomputed from the stored hazards on every read. Every link's severity and status is stated.
 */
class EarlierHazardChainTest {

    private static final String C1 = "01JCHECK000000000000000001";
    private static final String C2 = "01JCHECK000000000000000002";
    private static final String C3 = "01JCHECK000000000000000003";
    private static final String C4 = "01JCHECK000000000000000004";
    private static final String H1 = "01JHAZARD00000000000000001";
    private static final String H2 = "01JHAZARD00000000000000002";
    private static final String H3 = "01JHAZARD00000000000000003";
    private static final String H4 = "01JHAZARD00000000000000004";

    private static final ChecklistHazards FIRST = checklist(C1, 1, hazard(H1, IMPORTANT, OPEN, null));
    private static final ChecklistHazards SECOND = checklist(C2, 2, hazard(H2, IMPORTANT, OPEN, link(C1, H1)));

    @Test
    void secondVisitCarriesH1_noEarlierWarning() {
        List<EarlierHazard> earlier = EarlierHazardChain.of(List.of(FIRST), SECOND);

        assertEquals(List.of(new EarlierHazard(C1, 1, FIRST.hazards().get(0),
                List.of(new AddressedBy(C2, 2, H2, AddressKind.CARRIED)))), earlier);
        assertEquals(List.of(), open(earlier));
    }

    @Test
    void emptyThirdDraft_warnsH2Only() {
        assertEquals(List.of(H2), open(EarlierHazardChain.of(List.of(FIRST, SECOND), checklist(C3, 3))));
    }

    @Test
    void thirdLinksH2SeenResolved_noEarlierWarning() {
        assertEquals(List.of(), open(EarlierHazardChain.of(List.of(FIRST, SECOND),
                checklist(C3, 3, hazard(H3, IMPORTANT, OBSERVED_RESOLVED, link(C2, H2))))));
    }

    @Test
    void thirdDowngradedRemovedOrStillOpen_bringsH2BackOrCarriesIt() {
        assertEquals(List.of(H2), open(EarlierHazardChain.of(List.of(FIRST, SECOND),
                checklist(C3, 3, hazard(H3, MINOR, OPEN, link(C2, H2))))));
        assertEquals(List.of(H2), open(EarlierHazardChain.of(List.of(FIRST, SECOND), checklist(C3, 3))));
        assertEquals(List.of(), open(EarlierHazardChain.of(List.of(FIRST, SECOND),
                checklist(C3, 3, hazard(H3, IMPORTANT, OPEN, link(C2, H2))))));
    }

    @Test
    void minorSeenResolvedLink_addressesAndOwnerReportCarries() {
        assertEquals(List.of(), open(EarlierHazardChain.of(List.of(FIRST, SECOND),
                checklist(C3, 3, hazard(H3, MINOR, OBSERVED_RESOLVED, link(C2, H2))))));
        List<EarlierHazard> carried = EarlierHazardChain.of(List.of(FIRST, SECOND),
                checklist(C3, 3, hazard(H3, IMPORTANT, OWNER_REPORTED_FIXED, link(C2, H2))));
        assertEquals(List.of(), open(carried));
        assertEquals(AddressKind.CARRIED, carried.get(1).addressedBy().get(0).kind());
    }

    /** W16-39: a minor link that is not seen resolved keeps the earlier warning, also with an owner's report. */
    @Test
    void minorLinks_keepTheWarningUntilASeenResolvedLink() {
        ChecklistHazards minorOpen = checklist(C2, 2, hazard(H2, MINOR, OPEN, link(C1, H1)));
        ChecklistHazards minorReported = checklist(C2, 2, hazard(H2, MINOR, OWNER_REPORTED_FIXED, link(C1, H1)));

        assertEquals(List.of(H1), open(EarlierHazardChain.of(List.of(FIRST), minorOpen)));
        assertEquals(List.of(H1), open(EarlierHazardChain.of(List.of(FIRST), minorReported)));
        assertEquals(List.of(H1), open(EarlierHazardChain.of(List.of(FIRST, minorOpen), checklist(C3, 3))));
        assertEquals(List.of(), open(EarlierHazardChain.of(List.of(FIRST, minorOpen),
                checklist(C3, 3, hazard(H3, IMPORTANT, OBSERVED_RESOLVED, link(C1, H1))))));
        assertEquals(List.of(H1), open(EarlierHazardChain.of(List.of(FIRST, minorOpen),
                checklist(C3, 3, hazard(H3, MINOR, OPEN, link(C1, H1))))));
    }

    /** W16-43: a link from #4 straight to H1 skips #2 and #3; H2 still warns until linked. */
    @Test
    void skipLink_addressesH1WhileH2StillWarns() {
        ChecklistHazards second = checklist(C2, 2, hazard(H2, IMPORTANT, OPEN, null));
        ChecklistHazards third = checklist(C3, 3);
        ChecklistHazards fourth = checklist(C4, 4, hazard(H4, IMPORTANT, OBSERVED_RESOLVED, link(C1, H1)));

        assertEquals(List.of(H2), open(EarlierHazardChain.of(List.of(FIRST, second, third), fourth)));
    }

    /** W16-43: H carried by L in #2 and L resolved in #3 shows H as carried and no warning. */
    @Test
    void carriedThenResolved_listsEveryAddressingLink() {
        ChecklistHazards third = checklist(C3, 3, hazard(H3, IMPORTANT, OBSERVED_RESOLVED, link(C2, H2)),
                hazard(H4, IMPORTANT, OBSERVED_RESOLVED, link(C1, H1)));

        List<EarlierHazard> earlier = EarlierHazardChain.of(List.of(FIRST, SECOND), third);

        assertEquals(List.of(), open(earlier));
        assertEquals(List.of(new AddressedBy(C2, 2, H2, AddressKind.CARRIED), new AddressedBy(C3, 3, H4,
                AddressKind.RESOLVED)), earlier.get(0).addressedBy());
        assertEquals(List.of(new AddressedBy(C3, 3, H3, AddressKind.RESOLVED)), earlier.get(1).addressedBy());
    }

    @Test
    void minorOrSeenResolvedEarlierHazards_areNotListed() {
        ChecklistHazards first = checklist(C1, 1, hazard(H1, MINOR, OPEN, null),
                hazard(H2, IMPORTANT, OBSERVED_RESOLVED, null), hazard(H3, HazardSeverity.UNCLASSIFIED,
                        OWNER_REPORTED_FIXED, null));

        assertEquals(List.of(H3), EarlierHazardChain.of(List.of(first), checklist(C2, 2)).stream()
                .map(hazard -> hazard.hazard().hazardId()).toList());
    }

    @Test
    void fourVisits_repeatTheChainOneLevelFurther() {
        ChecklistHazards third = checklist(C3, 3, hazard(H3, IMPORTANT, OPEN, link(C2, H2)));

        assertEquals(List.of(H3), open(EarlierHazardChain.of(List.of(FIRST, SECOND, third), checklist(C4, 4))));
        assertEquals(List.of(), open(EarlierHazardChain.of(List.of(FIRST, SECOND, third),
                checklist(C4, 4, hazard(H4, MINOR, OBSERVED_RESOLVED, link(C3, H3))))));
    }

    // ==================== helpers ====================

    private static List<String> open(List<EarlierHazard> earlier) {
        return earlier.stream().filter(hazard -> hazard.addressedBy().isEmpty())
                .map(hazard -> hazard.hazard().hazardId()).toList();
    }

    private static ChecklistHazards checklist(String id, int no, Hazard... hazards) {
        return new ChecklistHazards(id, no, List.of(hazards));
    }

    private static HazardLink link(String checklistId, String hazardId) {
        return new HazardLink(checklistId, hazardId);
    }

    private static Hazard hazard(String id, HazardSeverity severity, HazardStatus status, HazardLink previous) {
        boolean reported = status == OWNER_REPORTED_FIXED;
        return new Hazard(id, null, previous, "Dây điện hở", severity, status, status == OPEN ? null : "Ghi chú",
                reported ? LocalDate.parse("2026-10-21") : null, reported ? "01JSA1E0000000000000000001" : null, null);
    }
}
