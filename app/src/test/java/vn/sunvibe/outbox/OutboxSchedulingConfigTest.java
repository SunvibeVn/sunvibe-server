package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.sql.DataSource;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import vn.sunvibe.shared.generator.id.IdGenerator;
import vn.sunvibe.shared.outbox.EventOutboxPort;

/**
 * The outbox's own threads (E05-S5b spec 4.10, S5b-D15 (2), S5b-AC-13): per consumer one run thread and one delivery
 * thread, each a one-thread executor with a queue of 1 and the abort policy; a thread is busy while a task submitted
 * to it has not finished, a cancelled one included. A test that awaits a thread's idleness waits on its executor's
 * {@code afterExecute}, which runs after the busy count fell; never on the task's future (E05S5b-P5).
 */
class OutboxSchedulingConfigTest {

	private static final long BOUND_SECONDS = 10;

	@Test
	void twoConsumers_oneRunAndOneDeliveryThreadEach() throws Exception {
		OutboxScheduling scheduling = new OutboxScheduling(List.of("x", "y"));
		try {
			AtomicReference<String> runX = new AtomicReference<>();
			AtomicReference<String> runY = new AtomicReference<>();
			scheduling.submitRun("x", () -> runX.set(Thread.currentThread().getName()))
					.get(BOUND_SECONDS, TimeUnit.SECONDS);
			scheduling.submitRun("y", () -> runY.set(Thread.currentThread().getName()))
					.get(BOUND_SECONDS, TimeUnit.SECONDS);
			String deliveryX = scheduling.submitDelivery("x", () -> Thread.currentThread().getName())
					.get(BOUND_SECONDS, TimeUnit.SECONDS);
			String deliveryY = scheduling.submitDelivery("y", () -> Thread.currentThread().getName())
					.get(BOUND_SECONDS, TimeUnit.SECONDS);

			assertThat(List.of(runX.get(), deliveryX, runY.get(), deliveryY))
					.containsExactly("outbox-run-x", "outbox-delivery-x", "outbox-run-y", "outbox-delivery-y");
			assertThat(scheduling.consumers()).containsExactly("x", "y");
		} finally {
			scheduling.destroy();
		}
	}

	@Test
	void consumerExecutors_oneThreadQueueOfOneAbortPolicy() {
		ThreadPoolExecutor executor = OutboxScheduling.newConsumerExecutor("outbox-delivery-x");
		try {
			assertThat(executor.getCorePoolSize()).isEqualTo(1);
			assertThat(executor.getMaximumPoolSize()).isEqualTo(1);
			assertThat(executor.getQueue()).isInstanceOf(ArrayBlockingQueue.class);
			assertThat(executor.getQueue().remainingCapacity()).isEqualTo(1);
			assertThat(executor.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void deliveryThread_cancelledTaskStillRunning_staysBusy() throws Exception {
		CountDownLatch afterExecute = new CountDownLatch(1);
		OutboxScheduling scheduling = new OutboxScheduling(List.of("x"), deliveryOf("x", afterExecute));
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			Future<Object> delivery = scheduling.submitDelivery("x", () -> {
				started.countDown();
				awaitIgnoringInterrupts(release);
				return null;
			});
			assertThat(started.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();

			delivery.cancel(true);

			assertThat(delivery.isDone()).as("a cancelled future reports done").isTrue();
			assertThat(scheduling.deliveryBusy("x")).as("the delivery still runs").isTrue();
			assertThat(scheduling.idle("x")).isFalse();
			release.countDown();
			assertThat(afterExecute.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
			assertThat(scheduling.deliveryBusy("x")).isFalse();
			assertThat(scheduling.idle("x")).isTrue();
		} finally {
			release.countDown();
			scheduling.destroy();
		}
	}

	@Test
	void deliveryThread_taskCancelledBeforeItStarted_idleAfterItsTurn() throws Exception {
		CountDownLatch afterExecute = new CountDownLatch(2);
		OutboxScheduling scheduling = new OutboxScheduling(List.of("x"), deliveryOf("x", afterExecute));
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicBoolean queuedRan = new AtomicBoolean();
		try {
			scheduling.submitDelivery("x", () -> {
				started.countDown();
				release.await(BOUND_SECONDS, TimeUnit.SECONDS);
				return null;
			});
			assertThat(started.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<Object> queued = scheduling.submitDelivery("x", () -> {
				queuedRan.set(true);
				return null;
			});
			assertThat(scheduling.deliveryTasks("x")).isEqualTo(2);

			queued.cancel(true);
			assertThat(scheduling.deliveryTasks("x")).as("counted until its thread passes over it").isEqualTo(2);
			release.countDown();

			assertThat(afterExecute.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
			assertThat(scheduling.deliveryTasks("x")).isZero();
			assertThat(scheduling.deliveryBusy("x")).isFalse();
			assertThat(queuedRan).isFalse();
		} finally {
			release.countDown();
			scheduling.destroy();
		}
	}

	@Test
	void productionList_noConsumerThread() throws Exception {
		OutboxScheduling scheduling = new OutboxScheduling(OutboxSubscriptions.production().consumers());
		try {
			assertThat(scheduling.consumers()).isEmpty();
			assertThat(scheduling.deliveriesOutlivingTheirRun()).isEmpty();
			assertThatThrownBy(() -> scheduling.submitRun("x", () -> { }))
					.isInstanceOf(IllegalArgumentException.class);

			AtomicReference<String> jobThread = new AtomicReference<>();
			CountDownLatch ran = new CountDownLatch(1);
			Runnable job = () -> {
				jobThread.compareAndSet(null, Thread.currentThread().getName());
				ran.countDown();
			};
			Duration tick = Duration.ofMillis(20);
			scheduling.scheduleJobs(job, tick, () -> { }, Duration.ofHours(1), () -> { }, Duration.ofHours(1));

			assertThat(ran.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
			assertThat(jobThread.get()).matches("outbox-\\d+");
		} finally {
			scheduling.destroy();
		}
	}

	@Test
	void deliveryThread_thirdSubmitRejected_countUnchanged() throws Exception {
		CountDownLatch afterExecute = new CountDownLatch(2);
		OutboxScheduling scheduling = new OutboxScheduling(List.of("x"), deliveryOf("x", afterExecute));
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			scheduling.submitDelivery("x", () -> {
				started.countDown();
				release.await(BOUND_SECONDS, TimeUnit.SECONDS);
				return null;
			});
			assertThat(started.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
			scheduling.submitDelivery("x", () -> null);

			assertThatThrownBy(() -> scheduling.submitDelivery("x", () -> null))
					.isInstanceOf(RejectedExecutionException.class);
			assertThat(scheduling.deliveryTasks("x")).isEqualTo(2);

			release.countDown();
			assertThat(afterExecute.await(BOUND_SECONDS, TimeUnit.SECONDS)).isTrue();
			assertThat(scheduling.deliveryTasks("x")).isZero();
		} finally {
			release.countDown();
			scheduling.destroy();
		}
	}

	// The configuration (OutboxConfig): jobs, keys and start-up checks

	@Test
	void workerDisabled_schedulesNothing() {
		runner.withPropertyValues("app.outbox.worker.enabled=false").run(context -> {
			assertThat(context).hasNotFailed();
			ready(context);

			assertThat(context.getBean(OutboxScheduling.class).registeredJobs()).isEmpty();
		});
	}

	@Test
	void workerEnabled_schedulesTheThreeJobs() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(OutboxScheduling.class).registeredJobs()).as("nothing before ready").isEmpty();
			ready(context);

			assertThat(context.getBean(OutboxScheduling.class).registeredJobs())
					.containsExactly("OutboxDeliveryJob", "OutboxCleanupJob", "OutboxStatusJob");
			assertThat(context.getBeansOfType(EventOutboxPort.class).values()).singleElement()
					.isInstanceOf(EventOutboxAdapter.class);
		});
	}

	@Test
	void jobRunThrows_logsRunFailedAndRunsAgain() throws Exception {
		CountDownLatch twoFailures = new CountDownLatch(2);
		ListAppender<ILoggingEvent> logs = new ListAppender<>() {

			@Override
			protected void append(ILoggingEvent event) {
				super.append(event);
				if (event.getFormattedMessage().startsWith("OUTBOX_RUN_FAILED ")) {
					twoFailures.countDown();
				}
			}
		};
		Logger jobLog = (Logger) LoggerFactory.getLogger(OutboxStatusJob.class);
		logs.start();
		jobLog.addAppender(logs);
		OutboxScheduling scheduling = new OutboxScheduling(List.of());
		try {
			OutboxWorker worker = new OutboxWorker(new JdbcTemplate(StandIns.failingDataSource()), new JsonMapper(),
					OutboxSubscriptions.production(), Map.of(), scheduling, Clock.systemUTC(),
					OutboxPostgresTestBase.limits(Duration.ofSeconds(1)));
			scheduling.scheduleJobs(() -> { }, Duration.ofHours(1), () -> { }, Duration.ofHours(1),
					new OutboxStatusJob(worker), Duration.ofMillis(50));

			assertThat(twoFailures.await(5, TimeUnit.SECONDS)).as("two runs failed, the job went on").isTrue();
		} finally {
			scheduling.destroy();
			jobLog.detachAppender(logs);
		}
		List<ILoggingEvent> failures = logs.list.stream()
				.filter(line -> line.getFormattedMessage().startsWith("OUTBOX_RUN_FAILED ")).toList();
		assertThat(failures.get(0).getLevel()).isEqualTo(Level.ERROR);
		assertThat(failures.get(0).getFormattedMessage())
				.isEqualTo("OUTBOX_RUN_FAILED job=OutboxStatusJob exception="
						+ "org.springframework.jdbc.CannotGetJdbcConnectionException");
		assertThat(failures).allSatisfy(line -> {
			assertThat(line.getFormattedMessage()).doesNotContain(StandIns.SECRET);
			assertThat(line.getThrowableProxy()).isNull();
		});
	}

	@Test
	void outboxScheduler_isNoTaskSchedulerOrExecutorBean() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			ready(context);

			assertThat(context.getBean(OutboxScheduling.class)).isNotInstanceOf(TaskScheduler.class)
					.isNotInstanceOf(Executor.class);
			assertThat(context.getBeansOfType(TaskScheduler.class).values())
					.noneMatch(scheduler -> scheduler.toString().contains("outbox-"));
			for (Executor executor : context.getBeansOfType(Executor.class).values()) {
				AtomicReference<String> thread = new AtomicReference<>();
				CompletableFuture.runAsync(() -> thread.set(Thread.currentThread().getName()), executor)
						.get(BOUND_SECONDS, TimeUnit.SECONDS);
				assertThat(thread.get()).doesNotStartWith("outbox-");
			}
		});
	}

	@Test
	void scheduledJobs_stayOnBootDefaultScheduler() {
		runner.withUserConfiguration(ScheduledProbe.class).run(context -> {
			assertThat(context).hasNotFailed();
			ready(context);

			assertThat(context.getBean(ScheduledProbe.class).thread.get(BOUND_SECONDS, TimeUnit.SECONDS))
					.startsWith("scheduling-");
		});
	}

	@Test
	void leaseNotLongerThanBatch_failsStartUp() {
		runner.withPropertyValues("app.outbox.delivery.lease-margin=0s").run(context -> assertThat(context)
				.hasFailed());
		runner.withPropertyValues("app.outbox.delivery.batch-size=51").run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("app.outbox.delivery.timeout=61s").run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues("app.outbox.delivery.poll-interval=0s")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void subscribedConsumerWithoutBean_failsStartUp() {
		subscribed(1, 0).run(context -> assertThat(context).hasFailed()
				.getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
		subscribed(1, 1).run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	void moreThanEightConsumers_failsStartUp() {
		subscribed(9, 9).run(context -> assertThat(context).hasFailed());
		subscribed(8, 8).run(context -> assertThat(context).hasNotFailed());
	}

	/** {@code subscribed} consumers in a primary list, and beans for the first {@code beans} of them. */
	private ApplicationContextRunner subscribed(int subscribed, int beans) {
		return runner.withBean("testSubscriptions", OutboxSubscriptions.class,
						() -> OutboxPostgresTestBase.subscriptions(names(subscribed)),
						definition -> definition.setPrimary(true))
				.withInitializer(context -> {
					for (String name : List.of(names(beans))) {
						context.getBeanFactory().registerSingleton("consumer-" + name,
								new OutboxPostgresTestBase.TestConsumer(name));
					}
				});
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
			.withUserConfiguration(StandIns.class, OutboxConfig.class)
			.withPropertyValues("app.outbox.worker.enabled=true", "app.outbox.delivery.poll-interval=5s",
					"app.outbox.delivery.batch-size=10", "app.outbox.delivery.timeout=10s",
					"app.outbox.delivery.lease-margin=30s", "app.outbox.cleanup.interval=1h",
					"app.outbox.cleanup.done-retention=7d", "app.outbox.status.interval=5m",
					"app.outbox.status.queue-age-alert=5m");

	private static void ready(ApplicationContext context) {
		ConfigurableApplicationContext source = ((AssertableApplicationContext) context)
				.getSourceApplicationContext(ConfigurableApplicationContext.class);
		source.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0], source, Duration.ZERO));
	}

	/** What OutboxConfig consumes; the asserted paths never call them (the data source refuses every connection). */
	@Configuration(proxyBeanMethods = false)
	static class StandIns {

		static final String SECRET = "a message with data";

		static DataSource failingDataSource() {
			return (DataSource) Proxy.newProxyInstance(StandIns.class.getClassLoader(), new Class<?>[] { DataSource.class },
					(proxy, method, args) -> switch (method.getName()) {
						case "toString" -> "failing data source";
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == args[0];
						default -> throw new SQLException(SECRET);
					});
		}

		@Bean
		DataSource dataSource() {
			return failingDataSource();
		}

		@Bean
		JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean
		EntityManager entityManager() {
			return (EntityManager) Proxy.newProxyInstance(StandIns.class.getClassLoader(),
					new Class<?>[] { EntityManager.class }, (proxy, method, args) -> switch (method.getName()) {
						case "toString" -> "stand-in entity manager";
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == args[0];
						default -> throw new UnsupportedOperationException(method.getName());
					});
		}

		@Bean("Snowflake")
		IdGenerator<Long> snowflake() {
			return () -> 1L;
		}

		@Bean
		ObjectMapper objectMapper() {
			return new JsonMapper();
		}
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
	}

	private static String[] names(int count) {
		String[] names = new String[count];
		for (int i = 0; i < count; i++) {
			names[i] = "c" + i;
		}
		return names;
	}

	/** The production executors, except {@code consumer}'s delivery executor, which counts {@code afterExecute} down. */
	static Function<String, ExecutorService> deliveryOf(String consumer, CountDownLatch afterExecute) {
		return name -> name.equals("outbox-delivery-" + consumer)
				? latched(name, afterExecute)
				: OutboxScheduling.newConsumerExecutor(name);
	}

	/** The production shape (core = max = 1, a queue of 1, abort policy) whose {@code afterExecute} counts down. */
	static ThreadPoolExecutor latched(String threadName, CountDownLatch afterExecute) {
		return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
			Thread thread = new Thread(runnable, threadName);
			thread.setDaemon(true);
			return thread;
		}, new ThreadPoolExecutor.AbortPolicy()) {

			@Override
			protected void afterExecute(Runnable runnable, Throwable thrown) {
				afterExecute.countDown();
			}
		};
	}

	/** A delivery that ignores its interrupts until {@code release} opens, then restores the flag. */
	static void awaitIgnoringInterrupts(CountDownLatch release) {
		boolean interrupted = false;
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS * 3);
		while (release.getCount() > 0 && System.nanoTime() < deadline) {
			try {
				release.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}
}
