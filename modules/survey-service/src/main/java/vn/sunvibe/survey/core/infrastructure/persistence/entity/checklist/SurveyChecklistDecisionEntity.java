package vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.survey.core.domain.model.checklist.DecisionOutcome;
import vn.sunvibe.survey.core.domain.model.checklist.FailedKind;
import vn.sunvibe.survey.core.domain.model.checklist.NeedsChangesKind;

import java.time.Instant;

/**
 * JPA Entity - the decision on a revision (survey-checklist submission spec 4.5, 5.3): insert-only. The reason, notes
 * and limitations are private: no {@code toString} prints them.
 */
@Entity
@Immutable
@Table(name = "survey_checklist_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistDecisionEntity {

    @Id
    @Column(name = "checklist_id", length = 26)
    private String checklistId;

    @Column(name = "outcome", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private DecisionOutcome outcome;

    @Column(name = "needs_changes_kind", length = 20)
    @Enumerated(EnumType.STRING)
    private NeedsChangesKind needsChangesKind;

    @Column(name = "failed_kind", length = 16)
    @Enumerated(EnumType.STRING)
    private FailedKind failedKind;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "limitations", length = 2000)
    private String limitations;

    @Column(name = "reviewer_user_id", nullable = false, length = 26)
    private String reviewerUserId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "lead_status_before", nullable = false, length = 30)
    private String leadStatusBefore;

    @Column(name = "lead_status_after", nullable = false, length = 30)
    private String leadStatusAfter;
}
