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

/** JPA Entity - SurveyMediaBodySlotEntity (survey-media spec 5.2, A-3): one content body in flight of a user. */
@Entity
@Table(name = "survey_media_body_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMediaBodySlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Column(name = "instance_id", nullable = false, length = 64)
    private String instanceId;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
