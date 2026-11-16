package vn.sunvibe.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

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
