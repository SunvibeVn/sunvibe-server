package vn.sunvibe.survey.core.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * JPA Entity - SurveyMediaVariantEntity (survey-media spec 5.2, I-3): one processed variant; insert-only, each key
 * written once. Keys and hashes are private: no {@code toString} prints them.
 */
@Entity
@Immutable
@Table(name = "survey_media_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMediaVariantEntity {

    @EmbeddedId
    private SurveyMediaVariantKey id;

    @Column(name = "attempt", nullable = false)
    private Integer attempt;

    @Column(name = "storage_key", nullable = false, length = 120)
    private String storageKey;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @Column(name = "processor_version", nullable = false, length = 100)
    private String processorVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Override
    public String toString() {
        return "SurveyMediaVariantEntity[" + (id == null ? null : id.getMediaId() + "/" + id.getVariant()) + "]";
    }
}
