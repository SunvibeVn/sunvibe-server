package vn.sunvibe.outbox;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * The outbox's own threads (ADR-014 decision 5; E05-S5b S5b-D15, spec 4.10): one scheduler thread ({@code outbox-})
 * for the three jobs, and per consumer a run thread ({@code outbox-run-<consumer>}) and a delivery thread
 * ({@code outbox-delivery-<consumer>}), each a one-thread executor with a queue of 1 and the abort policy, as survey
 * media builds its worker executor. None of them is a Spring bean of a scheduler or executor type, so Boot's default
 * scheduler keeps the modules' {@code @Scheduled} jobs.
 *
 * <p>A thread is busy while a task submitted to it has not finished: a count raised before the submit and lowered in
 * a {@code finally} around the task, so a task cancelled before it started is counted out when its thread passes over
 * it. Busy is never read from a future: a cancelled future reports done while its delivery may still run.
 */
public class OutboxScheduling implements DisposableBean {

    /** At most 1 + 2 x 8 outbox threads (S5b-D5). */
    static final int MAX_CONSUMERS = 8;

    private final Map<String, CountedExecutor> runs;
    private final Map<String, CountedExecutor> deliveries;
    private final ThreadPoolTaskScheduler scheduler;
    private final List<String> registered = new ArrayList<>();

    public OutboxScheduling(Collection<String> consumers) {
        this(consumers, OutboxScheduling::newConsumerExecutor);
    }

    /** {@code executors} builds the executor of a thread name; tests give one that counts its runs. */
    OutboxScheduling(Collection<String> consumers, Function<String, ExecutorService> executors) {
        if (consumers.size() > MAX_CONSUMERS) {
            throw new IllegalArgumentException("the outbox serves at most " + MAX_CONSUMERS + " consumers");
        }
        Map<String, CountedExecutor> runExecutors = new TreeMap<>();
        Map<String, CountedExecutor> deliveryExecutors = new TreeMap<>();
        for (String consumer : consumers) {
            runExecutors.put(consumer, new CountedExecutor(executors.apply("outbox-run-" + consumer)));
            deliveryExecutors.put(consumer, new CountedExecutor(executors.apply("outbox-delivery-" + consumer)));
        }
        this.runs = Collections.unmodifiableMap(runExecutors);
        this.deliveries = Collections.unmodifiableMap(deliveryExecutors);
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(1);
        this.scheduler.setThreadNamePrefix("outbox-");
        this.scheduler.setWaitForTasksToCompleteOnShutdown(false);
        this.scheduler.initialize();
    }

    /** One thread, a queue of 1, the abort policy: the shape of survey media's worker executor. */
    static ThreadPoolExecutor newConsumerExecutor(String threadName) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Submits a consumer's run to its run thread; a rejected submit throws {@link RejectedExecutionException}. */
    Future<?> submitRun(String consumer, Runnable run) {
        return executor(runs, consumer).submit(Executors.callable(run));
    }

    /** Submits one delivery to a consumer's delivery thread; a rejected submit throws {@link RejectedExecutionException}. */
    <T> Future<T> submitDelivery(String consumer, Callable<T> delivery) {
        return executor(deliveries, consumer).submit(delivery);
    }

    boolean runBusy(String consumer) {
        return executor(runs, consumer).unfinished() > 0;
    }

    boolean deliveryBusy(String consumer) {
        return executor(deliveries, consumer).unfinished() > 0;
    }

    /** Both of the consumer's threads have no unfinished task: only then may a run start. */
    boolean idle(String consumer) {
        return !runBusy(consumer) && !deliveryBusy(consumer);
    }

    /** The count of delivery tasks submitted and not yet finished. */
    int deliveryTasks(String consumer) {
        return executor(deliveries, consumer).unfinished();
    }

    /** Consumers whose delivery still runs while their run ended: a delivery that outlived its timeout. Sorted. */
    List<String> deliveriesOutlivingTheirRun() {
        List<String> names = new ArrayList<>();
        for (String consumer : runs.keySet()) {
            if (deliveryBusy(consumer) && !runBusy(consumer)) {
                names.add(consumer);
            }
        }
        return List.copyOf(names);
    }

    Set<String> consumers() {
        return runs.keySet();
    }

    /** The three jobs at fixed delays, each first run one interval from now. */
    public synchronized void scheduleJobs(Runnable delivery, Duration pollInterval, Runnable cleanUp,
                                          Duration cleanupInterval, Runnable status, Duration statusInterval) {
        schedule(delivery, pollInterval);
        schedule(cleanUp, cleanupInterval);
        schedule(status, statusInterval);
    }

    /** The class names of the scheduled jobs, in registration order. */
    public synchronized List<String> registeredJobs() {
        return List.copyOf(registered);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        runs.values().forEach(CountedExecutor::shutdownNow);
        deliveries.values().forEach(CountedExecutor::shutdownNow);
    }

    private void schedule(Runnable job, Duration interval) {
        scheduler.scheduleWithFixedDelay(job, scheduler.getClock().instant().plus(interval), interval);
        registered.add(job.getClass().getSimpleName());
    }

    private static CountedExecutor executor(Map<String, CountedExecutor> executors, String consumer) {
        CountedExecutor executor = executors.get(consumer);
        if (executor == null) {
            throw new IllegalArgumentException("the outbox has no thread for consumer " + consumer);
        }
        return executor;
    }

    /** An executor and its count of submitted, unfinished tasks. */
    private static final class CountedExecutor {

        private final ExecutorService executor;
        private final AtomicInteger unfinished = new AtomicInteger();

        CountedExecutor(ExecutorService executor) {
            this.executor = executor;
        }

        <T> Future<T> submit(Callable<T> task) {
            FutureTask<T> future = new FutureTask<>(task);
            unfinished.incrementAndGet();
            try {
                executor.execute(() -> {
                    try {
                        future.run();
                    } finally {
                        unfinished.decrementAndGet();
                    }
                });
            } catch (RejectedExecutionException e) {
                unfinished.decrementAndGet();
                throw e;
            }
            return future;
        }

        int unfinished() {
            return unfinished.get();
        }

        void shutdownNow() {
            executor.shutdownNow();
        }
    }
}
