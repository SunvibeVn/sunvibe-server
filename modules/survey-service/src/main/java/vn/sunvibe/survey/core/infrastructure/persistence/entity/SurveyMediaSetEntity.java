package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA Entity - SurveyMediaSetEntity (survey-media spec 5.2): the photos of one checklist. {@code set_version} is a
 * plain column changed under the row's {@code FOR UPDATE} lock, never a JPA {@code @Version}.
 */
@Entity
@Table(name = "survey_media_sets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMediaSetEntity {

    @Id
    @Column(name = "checklist_id", length = 26)
    private String checklistId;

    @Column(name = "set_version", nullable = false)
    private Long setVersion;

    @Column(name = "last_media_no", nullable = false)
    private Integer lastMediaNo;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
