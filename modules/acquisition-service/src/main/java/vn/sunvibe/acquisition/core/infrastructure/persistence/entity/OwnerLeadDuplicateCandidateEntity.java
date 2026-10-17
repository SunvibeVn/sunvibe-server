package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * JPA Entity - OwnerLeadDuplicateCandidateEntity
 * Table: owner_lead_duplicate_candidates
 * <p>
 * One row per pair of leads that may be the same owner (acquisition-owner-intake brief US-4); the system flags the
 * pair and never merges the leads. A row built in code is new: saving it is a plain insert, without the select a
 * merge of an assigned key would run first (the rows are written while the phone lock is held).
 */
@Entity
@Table(name = "owner_lead_duplicate_candidates", indexes = {
        @Index(name = "idx_owner_lead_duplicate_candidates_candidate", columnList = "candidate_lead_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerLeadDuplicateCandidateEntity implements Persistable<OwnerLeadDuplicateCandidateId> {

    @EmbeddedId
    private OwnerLeadDuplicateCandidateId id;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** False for a row built in code; set once the row is loaded or stored. Not a column. */
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean persisted;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    /** Loaded or just stored: a later save is an update. */
    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }
}
