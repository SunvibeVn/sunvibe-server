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
import vn.sunvibe.survey.core.domain.model.media.RemovalKind;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - a photo carried into a later revision (survey-checklist submission spec 4.6, 5.3). The correction is
 * private: no {@code toString} prints it.
 */
@Entity
@Table(name = "survey_checklist_media_selections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMediaSelectionEntity {

    @EmbeddedId
    private SurveyChecklistMediaKey id;

    @Column(name = "carried_from_checklist_id", nullable = false, length = 26)
    private String carriedFromChecklistId;

    @Column(name = "selected_at", nullable = false)
    private Instant selectedAt;

    @Column(name = "deselected_at")
    private Instant deselectedAt;

    @Column(name = "deselected_by_user_id", length = 26)
    private String deselectedByUserId;

    @Column(name = "deselection_kind", length = 10)
    @Enumerated(EnumType.STRING)
    private RemovalKind deselectionKind;

    @Column(name = "deselected_by_save_id")
    private Long deselectedBySaveId;

    @Column(name = "deselection_correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "deselection_correction_basis_on")
    private LocalDate correctionBasisOn;

    @Column(name = "deselection_correction_basis_text", length = 500)
    private String correctionBasisText;
}
