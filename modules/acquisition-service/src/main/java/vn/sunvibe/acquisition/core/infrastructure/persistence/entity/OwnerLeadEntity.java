package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadPropertyType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadSource;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - OwnerLeadEntity
 * Mapping of the OwnerLead domain model to the database.
 * Table: owner_leads
 * <p>
 * Consent, duplicate-of lead and assignee are plain id columns, not associations (the assignee has no foreign key:
 * auth-service owns the users). {@code version} is the optimistic lock of the lead queue; the contact data in this
 * table is private (acquisition module brief BR-2).
 */
@Entity
@Table(name = "owner_leads", indexes = {
        @Index(name = "idx_owner_leads_phone_created_at", columnList = "phone_normalized, created_at"),
        @Index(name = "idx_owner_leads_ward_code", columnList = "ward_code"),
        @Index(name = "idx_owner_leads_assigned", columnList = "assigned_user_id, status, created_at"),
        @Index(name = "idx_owner_leads_status_created_at", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerLeadEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "request_code", nullable = false, length = 12, unique = true)
    private String requestCode;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OwnerLeadStatus status;

    @Column(name = "intent", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OwnerLeadIntent intent;

    @Column(name = "source", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OwnerLeadSource source;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "phone_normalized", nullable = false, length = 16)
    private String phoneNormalized;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "province_code", nullable = false, length = 10)
    private String provinceCode;

    @Column(name = "province_name", nullable = false, length = 100)
    private String provinceName;

    @Column(name = "ward_code", nullable = false, length = 10)
    private String wardCode;

    @Column(name = "ward_name", nullable = false, length = 100)
    private String wardName;

    @Column(name = "address_detail", nullable = false, length = 255)
    private String addressDetail;

    @Column(name = "address_key", nullable = false, length = 255)
    private String addressKey;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "property_type", length = 20)
    @Enumerated(EnumType.STRING)
    private OwnerLeadPropertyType propertyType;

    @Column(name = "asking_price_amount", precision = 15, scale = 0)
    private BigDecimal askingPriceAmount;

    @Column(name = "asking_price_unit", length = 10)
    @Enumerated(EnumType.STRING)
    private PriceUnit askingPriceUnit;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "preferred_callback_time", length = 100)
    private String preferredCallbackTime;

    @Column(name = "consent_record_id", nullable = false, length = 26)
    private String consentRecordId;

    // TEXT in PostgreSQL; the length only sizes the column where Hibernate creates it (H2 context tests): a request is
    // at most 16 KB
    @Column(name = "submission_snapshot", nullable = false, length = 20000)
    private String submissionSnapshot;

    @Column(name = "assigned_user_id", length = 26)
    private String assignedUserId;

    @Column(name = "next_action_at")
    private Instant nextActionAt;

    @Column(name = "duplicate_of_lead_id", length = 26)
    private String duplicateOfLeadId;

    @Column(name = "close_reason", length = 500)
    private String closeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** The current requirement cycle (survey-schedule BR-10): 1 until the first requirement change. */
    @Column(name = "intent_cycle", nullable = false)
    private int intentCycle;
}
