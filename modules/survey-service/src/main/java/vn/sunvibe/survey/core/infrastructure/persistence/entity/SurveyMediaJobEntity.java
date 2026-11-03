package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.survey.core.domain.model.media.MediaJobKind;
import vn.sunvibe.survey.core.domain.model.media.MediaJobState;

import java.time.Instant;

/** JPA Entity - SurveyMediaJobEntity (survey-media spec 5.2): one processing or deletion job of a photo. */
@Entity
@Table(name = "survey_media_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMediaJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "media_id", nullable = false, length = 26)
    private String mediaId;

    @Column(name = "kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MediaJobKind kind;

    @Column(name = "state", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private MediaJobState state;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "limit_hits", nullable = false)
    private Integer limitHits;

    @Column(name = "lease_token", length = 26)
    private String leaseToken;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "last_error_code", length = 40)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
