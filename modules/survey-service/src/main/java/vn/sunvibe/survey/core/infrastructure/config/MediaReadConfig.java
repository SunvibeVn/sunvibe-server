package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.sunvibe.survey.core.application.shared.MediaReadSettings;

/** The content read bounds for the application layer (survey-media spec A-25). */
@Configuration(proxyBeanMethods = false)
public class MediaReadConfig {

    @Bean
    MediaReadSettings mediaReadSettings(MediaProperties media) {
        MediaProperties.Reads reads = media.getReads();
        return new MediaReadSettings(reads.getUserConcurrency(), reads.getInstanceConcurrency(),
                reads.getNoProgressTimeout(), reads.getFullDeadline(), reads.getPreviewDeadline(),
                reads.getThumbnailDeadline());
    }
}
