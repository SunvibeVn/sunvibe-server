package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.survey.core.domain.model.checklist.TakeoverKind;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - SurveyChecklistTakeoverEntity (survey-checklist spec 5.2): insert-only (survey BR-5), so
 * {@code @Immutable}; the checkpoint is the JSON of every group just before the takeover.
 */
@Entity
@Immutable
@Table(name = "survey_checklist_takeovers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistTakeoverEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "checklist_id", nullable = false, length = 26)
    private String checklistId;

    @Column(name = "takeover_no", nullable = false)
    private Integer takeoverNo;

    @Column(name = "kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TakeoverKind kind;

    @Column(name = "from_user_id", length = 26)
    private String fromUserId;

    @Column(name = "to_user_id", nullable = false, length = 26)
    private String toUserId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "basis_confirmed_on", nullable = false)
    private LocalDate basisConfirmedOn;

    @Column(name = "basis_text", nullable = false, length = 500)
    private String basisText;

    /** The checkpoint as JSON; TEXT in PostgreSQL. */
    @Column(name = "snapshot", length = 100_000_000)
    private String snapshot;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
