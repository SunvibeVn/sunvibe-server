package vn.sunvibe.acquisition.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentChannel;
import vn.sunvibe.acquisition.core.domain.model.consent.ConsentPurpose;

import java.time.Instant;

/**
 * JPA Entity - ConsentRecordEntity
 * Mapping of the ConsentRecord domain model to the database.
 * Table: consent_records
 * <p>
 * The IP address and user agent columns hold keyed hashes, never the raw values.
 */
@Entity
@Table(name = "consent_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentRecordEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "notice_version", nullable = false, length = 40)
    private String noticeVersion;

    @Column(name = "purpose", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private ConsentPurpose purpose;

    @Column(name = "channel", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ConsentChannel channel;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent_hash", length = 64)
    private String userAgentHash;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;
}
