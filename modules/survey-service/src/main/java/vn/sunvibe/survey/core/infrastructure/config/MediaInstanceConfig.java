package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.sunvibe.survey.core.application.shared.media.MediaInstanceSettings;

/** This instance's survey-media settings for the application layer (plan PD-12, spec A-3). */
@Configuration(proxyBeanMethods = false)
public class MediaInstanceConfig {

    @Bean
    MediaInstanceSettings mediaInstanceSettings(MediaProperties media) {
        return new MediaInstanceSettings(media.getInstanceId(), media.getBodies().getPerUser(),
                media.getBodies().getPerInstance());
    }
}
