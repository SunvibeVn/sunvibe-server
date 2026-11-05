package vn.sunvibe.survey.core.domain.policy.checklist;

import vn.sunvibe.survey.core.domain.model.checklist.SurveyChecklist;

import java.util.Optional;
import java.util.Set;

/**
 * Which lead can have its PASSED withdrawn (property-create requalification spec 4, D5-2, US-8 AC-3): a QUALIFIED lead,
 * which goes back to CONTACTED, and a held one, which stays; a closed lead never. The command and the survey block ask
 * here, so they cannot disagree.
 */
public final class RequalificationPolicy {

    private static final Set<String> LEAD_STATUSES = Set.of("QUALIFIED", "ON_HOLD");

    private RequalificationPolicy() {
    }

    public static boolean leadAllows(String leadStatus) {
        return LEAD_STATUSES.contains(leadStatus);
    }

    /**
     * @param seesEveryLead the viewer's access: every role that sees all leads may mark (property-create BR-17, D5-2)
     * @param currentPassed the unmarked PASSED of the lead's current cycle
     */
    public static RequalificationState state(boolean seesEveryLead, Optional<SurveyChecklist> currentPassed,
                                             String leadStatus) {
        if (!seesEveryLead || !leadAllows(leadStatus)) {
            return RequalificationState.NOT_ALLOWED;
        }
        return currentPassed.map(passed -> new RequalificationState(true, passed.getId().getValue(),
                passed.getVersion())).orElse(RequalificationState.NOT_ALLOWED);
    }
}
