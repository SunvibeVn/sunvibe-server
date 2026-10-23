package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity - SurveyAppointmentRevisionEntity
 * Mapping of one AppointmentRevision to the database.
 * Table: survey_appointment_revisions
 * <p>
 * Insert-only history (survey BR-5): {@code @Immutable} so Hibernate never updates a row.
 */
@Entity
@Immutable
@Table(name = "survey_appointment_revisions", indexes = {
        @Index(name = "idx_survey_appointment_revisions_appointment_valid_from",
                columnList = "appointment_id, valid_from")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyAppointmentRevisionEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "appointment_id", nullable = false, length = 26)
    private String appointmentId;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

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

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "actor_user_id", nullable = false, length = 26)
    private String actorUserId;
}
