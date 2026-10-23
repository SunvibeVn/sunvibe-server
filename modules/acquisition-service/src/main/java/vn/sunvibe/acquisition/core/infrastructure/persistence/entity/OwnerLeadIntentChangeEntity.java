package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import vn.sunvibe.acquisition.core.domain.model.lead.OwnerLeadIntent;
import vn.sunvibe.acquisition.core.domain.model.lead.PriceUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA Entity - OwnerLeadIntentChangeEntity
 * Mapping of the OwnerLeadIntentChange domain model to the database.
 * Table: owner_lead_intent_changes
 * <p>
 * Insert-only history of requirement changes (survey-schedule spec 5.3): {@code @Immutable} so Hibernate never updates
 * a row. The actor has no foreign key because auth-service owns the users.
 */
@Entity
@Immutable
@Table(name = "owner_lead_intent_changes", indexes = {
        @Index(name = "idx_owner_lead_intent_changes_lead_id_occurred_at", columnList = "lead_id, occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerLeadIntentChangeEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "lead_id", nullable = false, length = 26)
    private String leadId;

    @Column(name = "from_intent", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OwnerLeadIntent fromIntent;

    @Column(name = "to_intent", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OwnerLeadIntent toIntent;

    @Column(name = "from_price_amount", precision = 15, scale = 0)
    private BigDecimal fromPriceAmount;

    @Column(name = "from_price_unit", length = 10)
    @Enumerated(EnumType.STRING)
    private PriceUnit fromPriceUnit;

    @Column(name = "to_price_amount", precision = 15, scale = 0)
    private BigDecimal toPriceAmount;

    @Column(name = "to_price_unit", length = 10)
    @Enumerated(EnumType.STRING)
    private PriceUnit toPriceUnit;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "basis_confirmed_on", nullable = false)
    private LocalDate basisConfirmedOn;

    @Column(name = "basis_text", nullable = false, length = 500)
    private String basisText;

    @Column(name = "actor_user_id", nullable = false, length = 26)
    private String actorUserId;

    @Column(name = "new_intent_cycle", nullable = false)
    private int newIntentCycle;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
