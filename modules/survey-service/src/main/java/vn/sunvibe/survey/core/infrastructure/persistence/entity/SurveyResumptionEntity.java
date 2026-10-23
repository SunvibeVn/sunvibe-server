package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - SurveyResumptionEntity
 * Mapping of the SurveyResumption domain model to the database.
 * Table: survey_resumptions
 * <p>
 * Insert-only history (survey BR-5): {@code @Immutable} so Hibernate never updates a row.
 */
@Entity
@Immutable
@Table(name = "survey_resumptions", indexes = {
        @Index(name = "idx_survey_resumptions_lead_occurred_at", columnList = "lead_id, occurred_at"),
        @Index(name = "idx_survey_resumptions_visit_appointment_id", columnList = "visit_appointment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyResumptionEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "visit_appointment_id", nullable = false, length = 26)
    private String visitAppointmentId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "basis_confirmed_on", nullable = false)
    private LocalDate basisConfirmedOn;

    @Column(name = "basis_text", nullable = false, length = 500)
    private String basisText;

    @Column(name = "actor_user_id", nullable = false, length = 26)
    private String actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
