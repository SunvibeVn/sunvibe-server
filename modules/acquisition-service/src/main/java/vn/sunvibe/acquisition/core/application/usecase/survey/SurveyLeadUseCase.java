package vn.sunvibe.acquisition.core.application.usecase.survey;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Use Case - SurveyLeadUseCase
 * <p>
 * What acquisition publishes for survey-service (survey-schedule spec 5.4, ADR-009): survey commands run inside one
 * transaction that writes the lead row first ({@link #applySurveyMove}) and the survey rows after. The implementation
 * never calls survey-service back.
 */
public interface SurveyLeadUseCase {

    /**
     * The viewer of these authorities, by the lead queue's one role mapping; the authorities are already expanded
     * through the role hierarchy.
     *
     * @throws IllegalStateException none of the authorities may read owner leads (unreachable behind the endpoints'
     *                               role check)
     */
    SurveyLeadViewer viewerFor(String userId, Set<String> expandedAuthorities);

    /** The facts of the lead when it exists and the viewer may see it; empty otherwise (also for a malformed id). */
    Optional<SurveyLeadFacts> findVisible(String leadId, SurveyLeadViewer viewer);

    /**
     * Check the lead's version, apply the move of survey-schedule spec 4.3, save the lead (its version goes up) and
     * write its History entry. The caller has found the lead visible in the same transaction.
     *
     * @throws SurveyLeadVersionConflictException the version differs, or a concurrent command saved the lead first
     * @throws SurveyLeadMoveRefusedException     the move is not allowed from the lead's status
     */
    SurveyLeadMoveResult applySurveyMove(String leadId, long expectedVersion, SurveyLeadMove move);

    /**
     * The list cards of the given leads, keyed by lead id, in one query; leads the viewer cannot see (or unknown or
     * malformed ids) are absent.
     */
    Map<String, SurveyLeadCard> findCards(SurveyLeadViewer viewer, Collection<String> leadIds);

    /** Check a meeting place by the intake location rules; broken rules are returned, never thrown. */
    MeetingPlaceCheck checkMeetingPlace(String provinceCode, String wardCode, BigDecimal latitude,
                                        BigDecimal longitude);
}
