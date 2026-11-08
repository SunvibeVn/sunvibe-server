package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.exception.checklist.ChecklistValidationException;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.EvidenceRejection;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;
import vn.sunvibe.survey.core.domain.model.checklist.Violation;
import vn.sunvibe.survey.core.domain.model.media.SurveyMedia;
import vn.sunvibe.survey.core.domain.policy.media.EvidenceCoverageCalculator;
import vn.sunvibe.survey.core.domain.model.lead.LeadStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules of a decision (survey-checklist submission spec 4.5, US-3, D3-2, D3-5, survey-checklist BR-9, media BR-21 b). R1 checks the
 * body. R2..R6 refuse PASSED only, so a reviewer can still return or fail a record they cannot pass: R2 an open hazard
 * warning, R3 a rejected frozen photo, R4 a frozen photo held or without its content, R5 a SCHEDULED appointment, R6 a
 * required pair not covered by an accepted frozen photo. Every rule reads rows taken under the locks (I-6).
 */
public final class DecisionPolicy {

    private static final Set<WarningType> HAZARD_WARNINGS = Set.of(WarningType.OPEN_IMPORTANT_HAZARD,
            WarningType.EARLIER_HAZARD_OPEN);

    private DecisionPolicy() {
    }

    /**
     * What every decision needs (survey-checklist submission spec 4.5): the {@code errors[].field} of each state that fails now, in the order of
     * survey-checklist submission spec 5.5: the revision SUBMITTED, the lead SURVEY_SCHEDULED, the lead's current cycle, the current visit, serving
     * on (a reviewer must see the photos, A-3).
     */
    public static List<String> stateBlockers(ChecklistStatus status, LeadStatus leadStatus, int revisionIntentCycle,
                                             int leadIntentCycle, boolean onCurrentVisit, boolean servingOn) {
        List<String> blockers = new ArrayList<>();
        if (status != ChecklistStatus.SUBMITTED) {
            blockers.add("status");
        }
        if (leadStatus != LeadStatus.SURVEY_SCHEDULED) {
            blockers.add("lead.status");
        }
        if (revisionIntentCycle != leadIntentCycle) {
            blockers.add("lead.intentCycle");
        }
        if (!onCurrentVisit) {
            blockers.add("visit");
        }
        if (!servingOn) {
            blockers.add("media.serving");
        }
        return List.copyOf(blockers);
    }

    /** The {@code errors[].field} of every PASSED rule that fails now, in rule order (survey-checklist submission spec 7.3, {@code decisionBlockedBy}). */
    public static List<String> passedBlockers(DecisionFacts facts) {
        List<String> blockers = new ArrayList<>();
        if (facts.warnings().stream().anyMatch(warning -> HAZARD_WARNINGS.contains(warning.type()))) {
            blockers.add("hazards");
        }
        if (!facts.rejectedMediaIds().isEmpty()) {
            blockers.add("evidence.rejected");
        }
        if (facts.frozenPhotos().stream().anyMatch(photo -> photo.isHeld() || !photo.isContentPresent())) {
            blockers.add("evidence.unavailable");
        }
        if (facts.scheduledAppointment()) {
            blockers.add("appointment");
        }
        List<SurveyMedia> accepted = facts.frozenPhotos().stream()
                .filter(photo -> !facts.rejectedMediaIds().contains(photo.getId().getValue())).toList();
        if (!EvidenceCoverageCalculator.of(facts.g1(), facts.g4OrNull(), accepted).missing().isEmpty()) {
            blockers.add("evidence.coverage");
        }
        return List.copyOf(blockers);
    }

    /**
     * R1 on the body alone: each kind with exactly its outcome, a reason with both returns, limitations only with
     * PASSED, a photo marked once.
     *
     * @throws ChecklistValidationException every violation, with its body path
     */
    public static void checkBody(DecisionOutcome outcome, NeedsChangesKind needsChangesKind, FailedKind failedKind,
                                 String reason, String limitations, List<EvidenceRejection> rejected) {
        if (outcome == null) {
            throw new ChecklistValidationException(List.of(new Violation("outcome", "outcome is required.")));
        }
        List<Violation> violations = new ArrayList<>();
        boolean needsChanges = outcome == DecisionOutcome.NEEDS_CHANGES;
        boolean failed = outcome == DecisionOutcome.FAILED;
        if (needsChanges != (needsChangesKind != null)) {
            violations.add(new Violation("needsChangesKind", needsChanges ? "needsChangesKind is required."
                    : "needsChangesKind goes only with NEEDS_CHANGES."));
        }
        if (failed != (failedKind != null)) {
            violations.add(new Violation("failedKind", failed ? "failedKind is required."
                    : "failedKind goes only with FAILED."));
        }
        if ((needsChanges || failed) && reason == null) {
            violations.add(new Violation("reason", "reason is required."));
        }
        if (outcome != DecisionOutcome.PASSED && limitations != null) {
            violations.add(new Violation("limitations", "limitations go only with PASSED."));
        }
        Set<String> marked = new HashSet<>();
        for (int i = 0; i < rejected.size(); i++) {
            if (!marked.add(rejected.get(i).mediaId())) {
                violations.add(new Violation("rejectedEvidence." + i + ".mediaId", "A photo is marked once."));
            }
        }
        if (!violations.isEmpty()) {
            throw new ChecklistValidationException(violations);
        }
    }

    /**
     * R1 against the stored row: a reviewer marks only photos of the frozen set.
     *
     * @throws ChecklistValidationException every photo outside it
     */
    public static void checkRejections(List<EvidenceRejection> rejected, Set<String> frozenMediaIds) {
        List<Violation> violations = new ArrayList<>();
        for (int i = 0; i < rejected.size(); i++) {
            if (!frozenMediaIds.contains(rejected.get(i).mediaId())) {
                violations.add(new Violation("rejectedEvidence." + i + ".mediaId",
                        "Only a photo of the frozen set can be marked."));
            }
        }
        if (!violations.isEmpty()) {
            throw new ChecklistValidationException(violations);
        }
    }
}
