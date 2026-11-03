package vn.sunvibe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import vn.sunvibe.survey.core.infrastructure.config.MediaProfileGuard;
import vn.sunvibe.survey.core.infrastructure.config.MediaScheduling;
import vn.sunvibe.survey.core.infrastructure.config.MediaSchedulingConfig;

/**
 * The media scheduler pool (survey-media plan PD-4; SM-17 start-up): owned by {@link MediaSchedulingConfig}, never a
 * {@code TaskScheduler} or {@code Executor} bean, so Boot's default scheduler keeps running the auth and acquisition
 * {@code @Scheduled} jobs; nothing is scheduled while every adapter is {@code none}.
 */
class MediaSchedulingConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
			.withUserConfiguration(MediaProfileGuard.class, MediaSchedulingConfig.class);

	@Test
	void everyAdapterNone_registersNoTask() {
		runner.run(context -> {
			MediaScheduling scheduling = context.getBean(MediaScheduling.class);

			scheduling.scheduleUnlessEveryAdapterNone("sweeper", () -> { }, Duration.ofMinutes(5));
			scheduling.scheduleWorker("worker", () -> { }, Duration.ofSeconds(5));

			assertThat(scheduling.registeredTasks()).isEmpty();
		});
	}

	@Test
	void realAdapters_registerJobsAndTheWorkerOnlyWhileProcessingOn() {
		runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
				.withPropertyValues("app.survey.media.store=local", "app.survey.media.local.root=/tmp/m",
						"app.survey.media.scanner=stub", "app.survey.media.processor.kind=isolated",
						"app.survey.media.processor.version=dev-8.15.1",
						"app.survey.media.processing-enabled=false")
				.run(context -> {
					MediaScheduling scheduling = context.getBean(MediaScheduling.class);

					scheduling.scheduleUnlessEveryAdapterNone("sweeper", () -> { }, Duration.ofMinutes(5));
					scheduling.scheduleWorker("worker", () -> { }, Duration.ofSeconds(5));

					assertThat(scheduling.registeredTasks()).containsExactly("sweeper");
				});
	}

	@Test
	void mediaSchedulerAndExecutor_areNoTaskSchedulerOrExecutorBeans() {
		runner.run(context -> {
			assertThat(context.getBeansOfType(TaskScheduler.class).values())
					.noneMatch(scheduler -> scheduler.toString().contains("media-"));
			assertThat(context.containsBean("mediaTaskScheduler")).isFalse();
			assertThat(context.containsBean("mediaWorkerExecutor")).isFalse();
			for (Executor executor : context.getBeansOfType(Executor.class).values()) {
				AtomicInteger seen = new AtomicInteger();
				CompletableFuture.runAsync(() -> {
					if (Thread.currentThread().getName().startsWith("media-")) {
						seen.incrementAndGet();
					}
				}, executor).get(5, TimeUnit.SECONDS);
				assertThat(seen.get()).isZero();
			}
		});
	}

	@Test
	void authAndAcquisitionScheduledJobs_stayOnBootDefaultScheduler() {
		runner.withUserConfiguration(ScheduledProbe.class)
				.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
				.withPropertyValues("app.survey.media.store=local", "app.survey.media.local.root=/tmp/m",
						"app.survey.media.scanner=stub", "app.survey.media.processor.kind=isolated",
						"app.survey.media.processor.version=dev-8.15.1")
				.run(context -> {
					ScheduledProbe probe = context.getBean(ScheduledProbe.class);
					assertThat(probe.thread.get(5, TimeUnit.SECONDS)).startsWith("scheduling-");
				});
	}

	/** Stands for the modules' {@code @Scheduled} jobs. */
	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	static class ScheduledProbe {

		final CompletableFuture<String> thread = new CompletableFuture<>();

		@Scheduled(fixedDelay = 60_000)
		void run() {
			thread.complete(Thread.currentThread().getName());
		}

		@Bean
		String scheduledProbeMarker() {
			return "probe";
		}
	}
}
