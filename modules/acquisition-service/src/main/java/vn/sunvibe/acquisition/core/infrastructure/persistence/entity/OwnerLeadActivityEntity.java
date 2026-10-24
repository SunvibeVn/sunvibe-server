package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.acquisition.core.domain.model.activity.OwnerLeadActivityType;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadStatus;

import java.time.Instant;

/**
 * JPA Entity - OwnerLeadActivityEntity
 * Mapping of the OwnerLeadActivity domain model to the database.
 * Table: owner_lead_activities
 * <p>
 * Insert-only history (acquisition module brief BR-4): {@code @Immutable} so Hibernate never updates a row. The
 * actor and assignee columns have no foreign key because auth-service owns the users.
 */
@Entity
@Immutable
@Table(name = "owner_lead_activities", indexes = {
        @Index(name = "idx_owner_lead_activities_lead_id_occurred_at", columnList = "lead_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerLeadActivityEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OwnerLeadActivityType type;

    @Column(name = "actor_user_id", length = 26)
    private String actorUserId;

    @Column(name = "from_status", length = 30)
    @Enumerated(EnumType.STRING)
    private OwnerLeadStatus fromStatus;

    @Column(name = "to_status", length = 30)
    @Enumerated(EnumType.STRING)
    private OwnerLeadStatus toStatus;

    @Column(name = "from_assignee_id", length = 26)
    private String fromAssigneeId;

    @Column(name = "to_assignee_id", length = 26)
    private String toAssigneeId;

    @Column(name = "note", length = 2000)
    private String note;

    @Column(name = "next_action_at")
    private Instant nextActionAt;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /**
     * A survey appointment or a checklist, by type (no foreign key: survey-service owns both).
     */
    @Column(name = "reference_id", length = 26)
    private String referenceId;

    @Column(name = "reference_no")
    private Integer referenceNo;

    /** The checklist group of a CHECKLIST_SAVED entry (survey-checklist BR-15); null otherwise. */
    @Column(name = "reference_detail", length = 30)
    private String referenceDetail;
}
