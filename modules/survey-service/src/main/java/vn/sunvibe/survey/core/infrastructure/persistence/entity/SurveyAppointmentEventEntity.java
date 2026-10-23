package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentEventType;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;

import java.time.Instant;

/**
 * JPA Entity - SurveyAppointmentEventEntity
 * Mapping of one AppointmentEvent to the database.
 * Table: survey_appointment_events
 * <p>
 * Insert-only history (survey BR-5): {@code @Immutable} so Hibernate never updates a row.
 */
@Entity
@Immutable
@Table(name = "survey_appointment_events", indexes = {
        @Index(name = "idx_survey_appointment_events_appointment_occurred_at",
                columnList = "appointment_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyAppointmentEventEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "appointment_id", nullable = false, length = 26)
    private String appointmentId;

    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AppointmentEventType type;

    @Column(name = "actor_user_id", length = 26)
    private String actorUserId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "revision_no")
    private Integer revisionNo;

    @Column(name = "from_surveyor_id", length = 26)
    private String fromSurveyorId;

    @Column(name = "to_surveyor_id", length = 26)
    private String toSurveyorId;

    @Column(name = "confirmation_cause", length = 20)
    @Enumerated(EnumType.STRING)
    private ConfirmationCause confirmationCause;

    @Column(name = "note", length = 500)
    private String note;
}
