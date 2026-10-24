package vn.sunvibe.survey.core.application.shared.external.lead;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port to the owner lead, owned by acquisition-service (survey-schedule spec 5.4, ADR-009). The adapter lives in app.
 * Every call joins the caller's transaction; {@link #apply} writes the lead row first, before any survey row.
 * Checklist commands never write the lead row: they read it under a share lock first and add their History entry
 * last (survey-checklist spec 5.5).
 */
public interface SurveyLeadPort {

    /**
     * The viewer of these (already expanded) authorities by the lead queue's one role mapping.
     *
     * @throws IllegalStateException none of them may read owner leads
     */
    SurveyViewer viewer(String userId, Set<String> expandedAuthorities);

    /** The facts of the lead when it exists and the viewer may see it; empty otherwise. */
    Optional<LeadFacts> findVisible(String leadId, SurveyViewer viewer);

    /**
     * Check the lead's version, move it as the survey command asks, bump its version and write its History entry.
     *
     * @throws LeadVersionConflictException the version differs, or a concurrent command saved the lead first
     * @throws LeadMoveRefusedException     the lead's status does not allow the move
     */
    LeadMoveResult apply(String leadId, long expectedVersion, LeadMove move);

    /** The list cards of the leads the viewer can see, keyed by lead id; one query. */
    Map<String, LeadCard> cards(SurveyViewer viewer, Collection<String> leadIds);

    /** Check a meeting place by the intake location rules; broken rules are returned. */
    PlaceCheck checkPlace(String provinceCode, String wardCode, BigDecimal latitude, BigDecimal longitude);

    /**
     * The facts of a visible lead read under a {@code FOR SHARE} row lock held until the transaction ends
     * (survey-checklist spec 5.3, A-5): lead commands wait for the checklist command. Only inside a transaction.
     */
    Optional<LeadFacts> findVisibleForShare(String leadId, SurveyViewer viewer);

    /**
     * Insert the History entry of a checklist command as an activity row only: the lead's version and status never
     * change (survey-checklist spec 4.9). The caller found the lead visible in the same transaction.
     */
    ChecklistActivityResult recordChecklistActivity(String leadId, ChecklistActivity activity);

    /** Whether the lead exists, whoever may see it; only for the survey-block security event (spec 4.10). */
    boolean leadExists(String leadId);

    /** The owner's own web form values (spec A-18, A-27); empty for a lead from another source. */
    Optional<OwnerFormFacts> ownerForm(String leadId);

    /** The current assignee and every user named on the lead's History (spec A-25). */
    Set<String> leadStaffIds(String leadId);
}
