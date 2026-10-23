package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentKind;
import vn.sunvibe.survey.core.domain.model.appointment.AppointmentStatus;
import vn.sunvibe.survey.core.domain.model.appointment.CancelReason;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationCause;
import vn.sunvibe.survey.core.domain.model.appointment.ConfirmationState;
import vn.sunvibe.survey.core.domain.model.appointment.SurveyIntent;
import vn.sunvibe.survey.core.domain.model.appointment.VisitRecordKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - SurveyAppointmentEntity
 * Mapping of the SurveyAppointment domain model to the database.
 * Table: survey_appointments
 * <p>
 * The lead and the users are plain id columns (no foreign key: acquisition owns leads, auth owns users).
 * {@code version} is the optimistic lock of appointment commands. Places, notes and contacts are private.
 */
@Entity
@Table(name = "survey_appointments", indexes = {
        @Index(name = "idx_survey_appointments_surveyor_status_start",
                columnList = "surveyor_user_id, status, start_at"),
        @Index(name = "idx_survey_appointments_status_start", columnList = "status, start_at"),
        @Index(name = "idx_survey_appointments_lead_cycle_status", columnList = "lead_id, intent_cycle, status"),
        @Index(name = "idx_survey_appointments_previous_visit_id", columnList = "previous_visit_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyAppointmentEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "lead_id", nullable = false, length = 26, updatable = false)
    private String leadId;

    @Column(name = "appointment_no", nullable = false, updatable = false)
    private int appointmentNo;

    @Column(name = "kind", nullable = false, length = 10, updatable = false)
    @Enumerated(EnumType.STRING)
    private AppointmentKind kind;

    @Column(name = "previous_visit_id", length = 26, updatable = false)
    private String previousVisitId;

    @Column(name = "resurvey_reason", length = 500, updatable = false)
    private String resurveyReason;

    @Column(name = "intent", nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    private SurveyIntent intent;

    @Column(name = "intent_cycle", nullable = false, updatable = false)
    private int intentCycle;

    @Column(name = "status", nullable = false, length = 12)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "meeting_address", nullable = false, length = 255)
    private String meetingAddress;

    @Column(name = "meeting_province_code", nullable = false, length = 10)
    private String meetingProvinceCode;

    @Column(name = "meeting_province_name", nullable = false, length = 100)
    private String meetingProvinceName;

    @Column(name = "meeting_ward_code", nullable = false, length = 10)
    private String meetingWardCode;

    @Column(name = "meeting_ward_name", nullable = false, length = 100)
    private String meetingWardName;

    @Column(name = "meeting_latitude", precision = 9, scale = 6)
    private BigDecimal meetingLatitude;

    @Column(name = "meeting_longitude", precision = 9, scale = 6)
    private BigDecimal meetingLongitude;

    @Column(name = "access_note", length = 1000)
    private String accessNote;

    @Column(name = "on_site_contact_name", length = 100)
    private String onSiteContactName;

    @Column(name = "on_site_contact_role", length = 100)
    private String onSiteContactRole;

    @Column(name = "current_revision_no", nullable = false)
    private int currentRevisionNo;

    @Column(name = "lead_address_detail", nullable = false, length = 255, updatable = false)
    private String leadAddressDetail;

    @Column(name = "lead_province_code", nullable = false, length = 10, updatable = false)
    private String leadProvinceCode;

    @Column(name = "lead_province_name", nullable = false, length = 100, updatable = false)
    private String leadProvinceName;

    @Column(name = "lead_ward_code", nullable = false, length = 10, updatable = false)
    private String leadWardCode;

    @Column(name = "lead_ward_name", nullable = false, length = 100, updatable = false)
    private String leadWardName;

    @Column(name = "lead_latitude", precision = 9, scale = 6, updatable = false)
    private BigDecimal leadLatitude;

    @Column(name = "lead_longitude", precision = 9, scale = 6, updatable = false)
    private BigDecimal leadLongitude;

    @Column(name = "location_reason", length = 500, updatable = false)
    private String locationReason;

    @Column(name = "surveyor_user_id", nullable = false, length = 26)
    private String surveyorUserId;

    @Column(name = "confirmation_state", nullable = false, length = 12)
    @Enumerated(EnumType.STRING)
    private ConfirmationState confirmationState;

    @Column(name = "confirmation_cause", length = 20)
    @Enumerated(EnumType.STRING)
    private ConfirmationCause confirmationCause;

    @Column(name = "ever_confirmed", nullable = false)
    private boolean everConfirmed;

    @Column(name = "confirmation_changed_by", nullable = false, length = 26)
    private String confirmationChangedBy;

    @Column(name = "confirmation_changed_at", nullable = false)
    private Instant confirmationChangedAt;

    @Column(name = "confirmation_note", length = 500)
    private String confirmationNote;

    @Column(name = "visit_record_kind", length = 10)
    @Enumerated(EnumType.STRING)
    private VisitRecordKind visitRecordKind;

    @Column(name = "performed_by", length = 26)
    private String performedBy;

    @Column(name = "arrived_at")
    private Instant arrivedAt;

    @Column(name = "visit_revision_no")
    private Integer visitRevisionNo;

    @Column(name = "visit_note", length = 2000)
    private String visitNote;

    @Column(name = "visit_recorded_by", length = 26)
    private String visitRecordedBy;

    @Column(name = "visit_recorded_at")
    private Instant visitRecordedAt;

    @Column(name = "proxy_reason", length = 500)
    private String proxyReason;

    @Column(name = "basis_confirmed_on")
    private LocalDate basisConfirmedOn;

    @Column(name = "basis_text", length = 500)
    private String basisText;

    @Column(name = "cancel_reason", length = 20)
    @Enumerated(EnumType.STRING)
    private CancelReason cancelReason;

    @Column(name = "cancel_description", length = 2000)
    private String cancelDescription;

    @Column(name = "cancelled_by", length = 26)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_by", nullable = false, length = 26, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
