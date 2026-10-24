package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.survey.core.domain.model.log.AccessResourceType;

import java.time.Instant;

/**
 * JPA Entity - SurveyAccessLogEntity
 * Mapping of the SurveyAccessLog domain model to the database.
 * Table: survey_access_logs
 * <p>
 * Insert-only log (survey-schedule BR-5): {@code @Immutable} so Hibernate never updates a row.
 */
@Entity
@Immutable
@Table(name = "survey_access_logs", indexes = {
        @Index(name = "idx_survey_access_logs_lead_accessed_at", columnList = "lead_id, accessed_at"),
        @Index(name = "idx_survey_access_logs_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyAccessLogEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    /** What the read showed (survey-checklist spec A-10); null on rows written before the checklist. */
    @Column(name = "resource_type", length = 20)
    @Enumerated(EnumType.STRING)
    private AccessResourceType resourceType;

    @Column(name = "resource_id", length = 40)
    private String resourceId;
}
