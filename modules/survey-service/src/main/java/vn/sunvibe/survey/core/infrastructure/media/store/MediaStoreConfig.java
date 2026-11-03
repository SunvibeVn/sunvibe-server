package vn.sunvibe.survey.core.infrastructure.media.store;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.sunvibe.survey.core.application.shared.external.media.MediaObjectStore;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.StoreKind;

import java.net.URI;
import java.nio.file.Path;

/** The media store of {@code app.survey.media.store} (survey-media spec 5.1): none, local or S3 for R2. */
@Configuration(proxyBeanMethods = false)
public class MediaStoreConfig {

    @Bean
    MediaObjectStore mediaObjectStore(MediaProperties media) {
        StoreKind kind = media.getStore();
        if (kind == StoreKind.LOCAL) {
            if (media.getLocal().getRoot() == null || media.getLocal().getRoot().isBlank()) {
                throw new IllegalStateException("app.survey.media.local.root is required with store=local");
            }
            return new LocalMediaObjectStore(Path.of(media.getLocal().getRoot()));
        }
        if (kind == StoreKind.S3) {
            MediaProperties.S3 s3 = media.getS3();
            if (s3.getEndpoint() == null || s3.getQuarantineBucket() == null || s3.getMediaBucket() == null
                    || s3.getAccessKeyId() == null || s3.getAccessKeyId().isBlank()
                    || s3.getSecretAccessKey() == null || s3.getSecretAccessKey().isBlank()) {
                throw new IllegalStateException("app.survey.media.s3 needs endpoint, both buckets and credentials");
            }
            return S3MediaObjectStore.create(new S3ClientSettings(URI.create(s3.getEndpoint()), s3.getRegion(),
                    s3.getQuarantineBucket(), s3.getMediaBucket(), s3.getAccessKeyId(), s3.getSecretAccessKey(),
                    Math.max(32, media.getReads().getInstanceConcurrency()), media.getReads().getAcquisitionTimeout(),
                    media.getReads().getNoProgressTimeout()));
        }
        return new UnconfiguredMediaObjectStore();
    }
}
