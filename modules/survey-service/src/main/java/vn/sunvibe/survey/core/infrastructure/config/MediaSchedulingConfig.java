package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates {@link MediaScheduling} (survey-media plan PD-4). The jobs add themselves through it (worker, sweeper,
 * deletion job, full listing); this configuration registers nothing itself.
 */
@Configuration(proxyBeanMethods = false)
public class MediaSchedulingConfig {

    @Bean
    MediaScheduling mediaScheduling(MediaRuntimeMode mode, MediaProperties media) {
        return new MediaScheduling(mode, media.getWorker().getConcurrency());
    }
}
