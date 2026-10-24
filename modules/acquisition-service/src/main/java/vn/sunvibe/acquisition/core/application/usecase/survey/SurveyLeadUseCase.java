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

    /**
     * The same facts as {@link #findVisible}, read fresh under a PostgreSQL {@code FOR SHARE} row lock taken in the
     * caller's transaction (survey-checklist spec 5.3, A-5): lead commands wait for the checklist command, while other
     * checklist commands do not. It never updates the lead. Only inside a transaction (a share lock outside one would
     * end at once).
     */
    Optional<SurveyLeadFacts> findVisibleForShare(String leadId, SurveyLeadViewer viewer);

    /**
     * Insert the History entry of a checklist command as an activity row only (survey-checklist spec 4.9, I-5): the
     * lead row, its version and its status never change, and LAST UPDATE moves because the entry is the latest
     * activity. The caller found the lead visible in the same transaction.
     *
     * @return the latest activity time read back after the insert, and the entry with the actor's name
     */
    SurveyChecklistActivityResult recordChecklistActivity(String leadId, SurveyChecklistActivity activity);

    /**
     * Whether a lead with this id exists, whoever may see it; false for an id that is not a ULID. Only to decide
     * whether a refused survey-block read writes a security event (survey-checklist spec 4.10).
     */
    boolean leadExists(String leadId);

    /**
     * The values the owner's own web submission holds for the "owner's form" source (survey-checklist spec A-18,
     * A-27); empty for a lead from another source. The caller found the lead visible in the same transaction.
     */
    Optional<SurveyLeadOwnerForm> ownerForm(String leadId);

    /**
     * The lead's staff as acquisition knows them (survey-checklist spec A-25): the current assignee and every user
     * named on the lead's History (actor, previous and new assignee). The caller found the lead visible in the same
     * transaction.
     */
    Set<String> leadStaffIds(String leadId);
}
