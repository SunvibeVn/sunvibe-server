package vn.sunvibe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole application keeps Boot's one default scheduler: no module or app configuration adds a
 * {@code TaskScheduler} bean (which would switch Boot's off), and a {@code @Scheduled} job still runs on Boot's
 * {@code scheduling-} thread (performance-concurrency.md, the {@code @Scheduled} rule; MediaSchedulingConfigTest holds
 * the same for the media configuration alone).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SchedulerBeansTest.ScheduledProbe.class)
class SchedulerBeansTest {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private ScheduledProbe probe;

	@Test
	void taskSchedulerBeans_fullContext_onlyBootDefault() throws InterruptedException {
		Map<String, TaskScheduler> schedulers = context.getBeansOfType(TaskScheduler.class);

		assertThat(schedulers).hasSize(1);
		assertThat(probe.ran.await(10, TimeUnit.SECONDS)).as("the @Scheduled probe ran").isTrue();
		assertThat(probe.thread.get()).startsWith("scheduling-");
	}

	/** Stands for the modules' {@code @Scheduled} jobs. */
	@TestConfiguration(proxyBeanMethods = false)
	static class ScheduledProbe {

		final CountDownLatch ran = new CountDownLatch(1);
		final AtomicReference<String> thread = new AtomicReference<>();

		@Scheduled(fixedDelay = 50)
		void run() {
			thread.compareAndSet(null, Thread.currentThread().getName());
			ran.countDown();
		}
	}
}
