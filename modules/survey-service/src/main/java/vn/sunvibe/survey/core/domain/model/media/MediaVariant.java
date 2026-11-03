package vn.sunvibe.survey.core.domain.model.media;

import java.time.Instant;

/**
 * One processed variant of a READY photo (survey-media spec 4.5, 5.2, I-3): written once under
 * {@code media/<mediaId>/<attempt>/<variant>}, with its hash, size, dimensions and the deployment's processor version
 * (A-28). Storage keys and hashes are never printed or returned by the API.
 */
public record MediaVariant(VariantKind kind, int attempt, String storageKey, String sha256, long sizeBytes, int width,
                           int height, String processorVersion, Instant createdAt) {

    public MediaVariant {
        if (kind == null || storageKey == null || sha256 == null || processorVersion == null || createdAt == null) {
            throw new IllegalArgumentException("A variant has its kind, key, hash, processor version and time.");
        }
        if (attempt < 1 || sizeBytes < 1 || sizeBytes > kind.maxBytes() || width < 1 || height < 1) {
            throw new IllegalArgumentException("A variant has a positive attempt, size within its cap and "
                    + "dimensions.");
        }
    }

    @Override
    public String toString() {
        return "MediaVariant[" + kind + " attempt " + attempt + "]";
    }
}
