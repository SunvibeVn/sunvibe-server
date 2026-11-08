package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.ProcessorKind;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.ScannerKind;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.StoreKind;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The start-up guard of survey-media (survey-media spec A-1, A-28). An environment is non-production only when its active-profile
 * set is non-empty and entirely inside {dev, test, lan}; an empty set counts as production. Elsewhere a configuration
 * error refuses the start: a local store, the stub scanner, an {@code s3.endpoint} that is not https, uploads on while
 * an adapter is {@code none}, processing on while some but not all adapters are {@code none}. A configured processor
 * version must match the variant column's pattern in every environment. With every adapter {@code none} processing is
 * effectively off. The messages name settings, never values.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaProperties.class)
public class MediaProfileGuard {

    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("dev", "test", "lan");
    /** survey-media spec 5.2: the CHECK of survey_media_variants.processor_version (at most 100 characters). */
    private static final Pattern PROCESSOR_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@/+-]{0,99}");

    private final MediaRuntimeMode mode;

    public MediaProfileGuard(Environment environment, MediaProperties media) {
        String[] profiles = environment.getActiveProfiles();
        boolean nonProduction = profiles.length > 0
                && Arrays.stream(profiles).allMatch(NON_PRODUCTION_PROFILES::contains);
        boolean storeNone = media.getStore() == StoreKind.NONE;
        boolean scannerNone = media.getScanner() == ScannerKind.NONE;
        boolean processorNone = media.getProcessor().getKind() == ProcessorKind.NONE;
        boolean everyNone = storeNone && scannerNone && processorNone;
        boolean anyNone = storeNone || scannerNone || processorNone;

        if (!processorNone) {
            String version = media.getProcessor().getVersion();
            if (version == null || !PROCESSOR_VERSION.matcher(version).matches()) {
                throw new IllegalStateException("app.survey.media.processor.version must match the variant pattern "
                        + "and have at most 100 characters (spec A-28)");
            }
        }
        if (!nonProduction) {
            if (media.getStore() == StoreKind.LOCAL) {
                throw new IllegalStateException("app.survey.media store=local is for dev, test and lan only");
            }
            if (media.getScanner() == ScannerKind.STUB) {
                throw new IllegalStateException("app.survey.media scanner=stub is for dev, test and lan only");
            }
            if (media.getStore() == StoreKind.S3 && (media.getS3().getEndpoint() == null
                    || !media.getS3().getEndpoint().startsWith("https://"))) {
                throw new IllegalStateException("app.survey.media.s3.endpoint must be https outside dev, test and "
                        + "lan");
            }
            if (media.isUploadEnabled() && anyNone) {
                throw new IllegalStateException("app.survey.media.upload-enabled needs every adapter configured");
            }
            if (media.isProcessingEnabled() && anyNone && !everyNone) {
                throw new IllegalStateException("app.survey.media.processing-enabled with some but not all adapters "
                        + "none");
            }
        }
        this.mode = new MediaRuntimeMode(nonProduction, everyNone, media.isUploadEnabled() && !anyNone,
                media.isProcessingEnabled() && !anyNone, media.isServingEnabled());
    }

    @Bean
    MediaRuntimeMode mediaRuntimeMode() {
        return mode;
    }
}
