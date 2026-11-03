package vn.sunvibe.survey.core.infrastructure.media.store;

import java.net.URI;
import java.time.Duration;

/**
 * The connection settings of the S3 (R2) store (survey-media spec 4.12, A-25). The secret key is never printed.
 *
 * @param getConnections     the GET client's pool, at least the instance's read cap (32)
 * @param acquisitionTimeout how long a content GET waits for a free connection before 503 MEDIA_STORE_UNAVAILABLE
 * @param socketTimeout      the idle read timeout of every client
 */
public record S3ClientSettings(URI endpoint, String region, String quarantineBucket, String mediaBucket,
                               String accessKeyId, String secretAccessKey, int getConnections,
                               Duration acquisitionTimeout, Duration socketTimeout) {

    @Override
    public String toString() {
        return "S3ClientSettings[region=" + region + ", getConnections=" + getConnections + "]";
    }
}
