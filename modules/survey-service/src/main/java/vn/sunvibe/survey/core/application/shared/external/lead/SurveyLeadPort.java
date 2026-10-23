package vn.sunvibe.survey.core.application.shared.external.lead;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Port to the owner lead, owned by acquisition-service (survey-schedule spec 5.4, ADR-009). The adapter lives in app.
 * Every call joins the caller's transaction; {@link #apply} writes the lead row first, before any survey row.
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
}
