package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * JPA Entity - a frozen photo the reviewer did not accept (survey-checklist submission spec A-5, 5.3): insert-only. The
 * reason is private: no {@code toString} prints it.
 */
@Entity
@Immutable
@Table(name = "survey_checklist_evidence_rejections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistEvidenceRejectionEntity {

    @EmbeddedId
    private SurveyChecklistMediaKey id;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
}
