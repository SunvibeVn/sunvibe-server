package vn.sunvibe.survey.core.domain.model.checklist;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Why a PASSED revision was withdrawn from use (property-create requalification spec 4, 5.2, A-3): kind, reason, source
 * and scope of the problem, the day the source confirmed it, who marked and when, and the lead status around the move;
 * insert-only. The reason, source and scope are private: never in History, logs or list views (survey BR-4).
 */
public record ChecklistRequalification(SurveyChecklistId checklistId, RequalificationKind kind, String reason,
                                       String eventSource, String scope, LocalDate basisDate, String markedByUserId,
                                       Instant markedAt, String leadStatusBefore, String leadStatusAfter) {

    public ChecklistRequalification {
        if (checklistId == null || kind == null || reason == null || eventSource == null || scope == null
                || basisDate == null || markedByUserId == null || markedAt == null || leadStatusBefore == null
                || leadStatusAfter == null) {
            throw new IllegalArgumentException("A requalification needs its revision, kind, texts, date, actor, time "
                    + "and lead moves.");
        }
    }

    @Override
    public String toString() {
        return "ChecklistRequalification[" + checklistId + " " + kind + "]";
    }
}
