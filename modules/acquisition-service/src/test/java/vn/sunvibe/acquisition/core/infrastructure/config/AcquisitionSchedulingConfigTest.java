package vn.sunvibe.acquisition.core.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import vn.sunvibe.acquisition.core.application.usecase.IdempotencyKeyCleanupUseCase;
import vn.sunvibe.acquisition.core.infrastructure.scheduling.IdempotencyKeyCleanupJob;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acquisition jobs are scheduled by the module itself: in a context with only acquisition beans (no other
 * module's configuration), the idempotency key cleanup is registered with its daily cron.
 */
class AcquisitionSchedulingConfigTest {

    @Test
    void context_withOnlyAcquisitionBeans_registersTheIdempotencyKeyCleanupJob() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(IdempotencyKeyCleanupUseCase.class, () -> () -> 0);
            context.register(AcquisitionSchedulingConfig.class, IdempotencyKeyCleanupJob.class);
            context.refresh();

            List<String> tasks = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                    .flatMap(holder -> holder.getScheduledTasks().stream())
                    .map(ScheduledTask::toString)
                    .toList();

            assertTrue(tasks.stream().anyMatch(task ->
                    task.contains("IdempotencyKeyCleanupJob.purgeExpiredIdempotencyKeys")), tasks.toString());
        }
    }
}
