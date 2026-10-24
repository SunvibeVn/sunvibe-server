package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistKind;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistStatus;

import java.time.Instant;

/**
 * JPA Entity - SurveyChecklistEntity (survey-checklist spec 5.2): one checklist revision; the version guards the
 * takeover's change of holder.
 */
@Entity
@Table(name = "survey_checklists", indexes = {
        @Index(name = "idx_survey_checklists_visit_appointment_id", columnList = "visit_appointment_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "checklist_no", nullable = false)
    private Integer checklistNo;

    @Column(name = "visit_appointment_id", nullable = false, length = 26)
    private String visitAppointmentId;

    @Column(name = "intent_cycle", nullable = false)
    private Integer intentCycle;

    @Column(name = "holder_user_id", nullable = false, length = 26)
    private String holderUserId;

    @Column(name = "template_code", nullable = false, length = 30)
    private String templateCode;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @Column(name = "kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChecklistKind kind;

    @Column(name = "status", nullable = false, length = 12)
    @Enumerated(EnumType.STRING)
    private ChecklistStatus status;

    @Column(name = "created_by", nullable = false, length = 26)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
