package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.sunvibe.survey.core.application.shared.media.MediaReadBudgetLimits;
import vn.sunvibe.survey.core.application.shared.media.MediaReadSettings;

/** The content read bounds and the read budgets for the application layer (survey-media spec A-25, A-26). */
@Configuration(proxyBeanMethods = false)
public class MediaReadConfig {

    @Bean
    MediaReadSettings mediaReadSettings(MediaProperties media) {
        MediaProperties.Reads reads = media.getReads();
        return new MediaReadSettings(reads.getUserConcurrency(), reads.getInstanceConcurrency(),
                reads.getNoProgressTimeout(), reads.getFullDeadline(), reads.getPreviewDeadline(),
                reads.getThumbnailDeadline());
    }

    @Bean
    MediaReadBudgetLimits mediaReadBudgetLimits(MediaProperties media) {
        MediaProperties.Budgets budgets = media.getBudgets();
        return new MediaReadBudgetLimits(budgets.getFull(), budgets.getPreview(), budgets.getThumbnail(),
                budgets.getList());
    }
}
