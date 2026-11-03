package vn.sunvibe.survey.core.infrastructure.media.processor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.sunvibe.survey.core.application.shared.external.media.ImageNormalizer;
import vn.sunvibe.survey.core.application.shared.external.media.ProcessorUnavailableException;
import vn.sunvibe.survey.core.domain.model.media.ProbeResult;
import vn.sunvibe.survey.core.domain.model.media.SourceFormat;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties;
import vn.sunvibe.survey.core.infrastructure.config.MediaProperties.ProcessorKind;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * The processor of {@code app.survey.media.processor} (survey-media spec 5.1, ADR-011 decision 4 form (a)): the
 * isolated container over its TCP channel, or none. Normalize may run up to the container's 120 s wall clock.
 */
@Configuration(proxyBeanMethods = false)
public class MediaProcessorConfig {

    static final Duration CONNECT_AND_DETECT = Duration.ofSeconds(30);
    static final Duration NORMALIZE = Duration.ofSeconds(130);

    @Bean
    ImageNormalizer imageNormalizer(MediaProperties media) {
        MediaProperties.Processor processor = media.getProcessor();
        if (processor.getKind() == ProcessorKind.ISOLATED) {
            if (processor.getHost() == null || processor.getHost().isBlank() || processor.getPort() <= 0) {
                throw new IllegalStateException("app.survey.media.processor needs host and port with kind=isolated");
            }
            return new IsolatedProcessorClient(processor.getHost(), processor.getPort(), processor.getVersion(),
                    CONNECT_AND_DETECT, NORMALIZE);
        }
        return new ImageNormalizer() {
            @Override
            public Optional<SourceFormat> detect(InputStream source, long length) {
                throw new ProcessorUnavailableException("PROCESSOR_NOT_CONFIGURED");
            }

            @Override
            public NormalizeResult normalize(InputStream source, long length) {
                throw new ProcessorUnavailableException("PROCESSOR_NOT_CONFIGURED");
            }

            @Override
            public ProbeResult probe() {
                return ProbeResult.INCOMPLETE;
            }

            @Override
            public String processorVersion() {
                return "none";
            }
        };
    }
}
