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
import vn.sunvibe.survey.core.domain.model.checklist.RequalificationKind;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - the reason of a requalification mark (property-create requalification spec 5.2): insert-only. The reason,
 * source and scope are private: no {@code toString} prints them.
 */
@Entity
@Immutable
@Table(name = "survey_checklist_requalifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistRequalificationEntity {

    @Id
    @Column(name = "checklist_id", length = 26)
    private String checklistId;

    @Column(name = "reason_kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RequalificationKind reasonKind;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Column(name = "event_source", nullable = false, length = 500)
    private String eventSource;

    @Column(name = "scope", nullable = false, length = 500)
    private String scope;

    @Column(name = "basis_date", nullable = false)
    private LocalDate basisDate;

    @Column(name = "marked_by_user_id", nullable = false, length = 26)
    private String markedByUserId;

    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    @Column(name = "lead_status_before", nullable = false, length = 30)
    private String leadStatusBefore;

    @Column(name = "lead_status_after", nullable = false, length = 30)
    private String leadStatusAfter;
}
