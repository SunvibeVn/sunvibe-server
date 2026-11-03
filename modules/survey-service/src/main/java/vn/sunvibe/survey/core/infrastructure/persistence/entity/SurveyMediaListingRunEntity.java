package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA Entity - SurveyMediaListingRunEntity (survey-media spec 5.2, 4.4 step 4): one leased, resumable full listing of
 * the media bucket. The last listed key is a storage key: no {@code toString} prints it.
 */
@Entity
@Table(name = "survey_media_listing_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMediaListingRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "lease_token", length = 26)
    private String leaseToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "start_after_key", length = 120)
    private String startAfterKey;

    @Column(name = "keys_seen", nullable = false)
    private Long keysSeen;

    @Column(name = "undeleted_count", nullable = false)
    private Integer undeletedCount;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Override
    public String toString() {
        return "SurveyMediaListingRunEntity[" + id + "]";
    }
}
