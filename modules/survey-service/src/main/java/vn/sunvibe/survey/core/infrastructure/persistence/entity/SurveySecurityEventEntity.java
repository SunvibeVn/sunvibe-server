package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.survey.core.domain.model.security.SecurityAction;
import vn.sunvibe.survey.core.domain.model.security.SecurityOutcome;
import vn.sunvibe.survey.core.domain.model.security.SecurityResourceType;

import java.time.Instant;

/** JPA Entity - SurveySecurityEventEntity (survey-checklist spec 5.2, 4.10): insert-only, so {@code @Immutable}. */
@Entity
@Immutable
@Table(name = "survey_security_events", indexes = {
        @Index(name = "idx_survey_security_events_lead_occurred_at", columnList = "lead_id, occurred_at"),
        @Index(name = "idx_survey_security_events_user_occurred_at", columnList = "user_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveySecurityEventEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "resource_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SecurityResourceType resourceType;

    @Column(name = "resource_id", nullable = false, length = 40)
    private String resourceId;

    @Column(name = "action", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private SecurityAction action;

    @Column(name = "outcome", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SecurityOutcome outcome;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
