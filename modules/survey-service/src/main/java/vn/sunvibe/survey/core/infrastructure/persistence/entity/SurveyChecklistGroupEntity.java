package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA Entity - SurveyChecklistGroupEntity (survey-checklist spec 5.2): the JSON document of one group, its version and
 * its last save. The version guards every save of the group.
 */
@Entity
@Table(name = "survey_checklist_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistGroupEntity {

    @EmbeddedId
    private SurveyChecklistGroupKey id;

    /** The group document as JSON (PD-1); TEXT in PostgreSQL. */
    @Column(name = "content", nullable = false, length = 10_000_000)
    private String content;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_by", length = 26)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
