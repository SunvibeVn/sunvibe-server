package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistGroup;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - SurveyChecklistSaveEntity (survey-checklist spec 5.2): one row of the insert-only save log, so
 * {@code @Immutable}; the correction is all set or all empty.
 */
@Entity
@Immutable
@Table(name = "survey_checklist_saves", indexes = {
        @Index(name = "idx_survey_checklist_saves_checklist_saved_at", columnList = "checklist_id, saved_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistSaveEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "checklist_id", nullable = false, length = 26)
    private String checklistId;

    @Column(name = "group_key", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChecklistGroup groupKey;

    @Column(name = "group_version", nullable = false)
    private Long groupVersion;

    @Column(name = "actor_user_id", nullable = false, length = 26)
    private String actorUserId;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "correction_basis_confirmed_on")
    private LocalDate correctionBasisConfirmedOn;

    @Column(name = "correction_basis_text", length = 500)
    private String correctionBasisText;

    /** JSON array of {unitKey, spaceCode}; TEXT in PostgreSQL. */
    @Column(name = "corrected_units", length = 1_000_000)
    private String correctedUnits;
}
