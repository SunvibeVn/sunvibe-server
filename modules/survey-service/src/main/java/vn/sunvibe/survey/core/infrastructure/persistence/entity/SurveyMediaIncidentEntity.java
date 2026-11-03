package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** JPA Entity - SurveyMediaIncidentEntity (survey-media spec 5.2, A-18): an overdue deletion, ids and counts only. */
@Entity
@Table(name = "survey_media_incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMediaIncidentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "kind", nullable = false, length = 30)
    private String kind;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "first_overdue_at", nullable = false)
    private Instant firstOverdueAt;

    @Column(name = "media_count", nullable = false)
    private Integer mediaCount;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
