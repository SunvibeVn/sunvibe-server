package vn.sunvibe.survey.core.infrastructure.persistence.entity.checklist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.survey.core.domain.model.checklist.ChecklistKind;
import vn.sunvibe.survey.core.domain.model.checklist.PropertyTypeCode;
import vn.sunvibe.survey.core.domain.model.checklist.RentalScope;

import java.time.Instant;

/** JPA Entity - one submission of a revision (survey-checklist submission spec 4.3, 5.3): insert-only. */
@Entity
@Immutable
@Table(name = "survey_checklist_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyChecklistSubmissionEntity {

    @Id
    @Column(name = "checklist_id", length = 26)
    private String checklistId;

    @Column(name = "submitted_by_user_id", nullable = false, length = 26)
    private String submittedByUserId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "template_code", nullable = false, length = 30)
    private String templateCode;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion;

    @Column(name = "kind", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChecklistKind kind;

    @Column(name = "lead_intent", nullable = false, length = 20)
    private String leadIntent;

    @Column(name = "intent_cycle", nullable = false)
    private Integer intentCycle;

    @Column(name = "visit_appointment_id", nullable = false, length = 26)
    private String visitAppointmentId;

    @Column(name = "visit_performed_by", nullable = false, length = 26)
    private String visitPerformedBy;

    @Column(name = "visit_arrived_at", nullable = false)
    private Instant visitArrivedAt;

    @Column(name = "visit_recorded_by", nullable = false, length = 26)
    private String visitRecordedBy;

    @Column(name = "visit_recorded_at", nullable = false)
    private Instant visitRecordedAt;

    @Column(name = "rental_scope", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private RentalScope rentalScope;

    @Column(name = "property_type", length = 30)
    @Enumerated(EnumType.STRING)
    private PropertyTypeCode propertyType;

    /** JSON array of {spaceId, code}; TEXT in PostgreSQL. */
    @Column(name = "spaces", nullable = false, length = 1_000_000)
    private String spaces;

    /** JSON object of group name to version; TEXT in PostgreSQL. */
    @Column(name = "group_versions", nullable = false, length = 1_000_000)
    private String groupVersions;

    @Column(name = "media_policy", nullable = false, length = 30)
    private String mediaPolicy;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;
}
