package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.survey.core.domain.model.media.EvidenceCategory;

/** JPA Entity - one photo of a submission's frozen set (survey-checklist submission spec 4.3, 5.3): insert-only. */
@Entity
@Immutable
@Table(name = "survey_checklist_evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistEvidenceEntity {

    @EmbeddedId
    private SurveyChecklistMediaKey id;

    @Column(name = "category", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EvidenceCategory category;

    @Column(name = "space_id", length = 26)
    private String spaceId;

    @Column(name = "space_code", length = 20)
    private String spaceCode;

    @Column(name = "full_sha256", nullable = false, length = 64)
    private String fullSha256;
}
