package vn.sunvibe.survey.core.infrastructure.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The media scheduler pool (survey-media plan PD-4): a {@code ThreadPoolTaskScheduler} of 4 threads ({@code media-})
 * and a bounded worker executor (core = max = worker concurrency), both owned here and never beans of a scheduler or
 * executor type, so Boot's default scheduler keeps the modules' {@code @Scheduled} jobs. With every adapter
 * {@code none} nothing is scheduled; the worker only while processing is on; the sweeper, probes, deletion job and
 * full listing whatever processing says, so clean-up goes on while processing is off.
 */
public class MediaScheduling implements DisposableBean {

    private final MediaRuntimeMode mode;
    private final ThreadPoolTaskScheduler scheduler;
    private final ThreadPoolExecutor workerExecutor;
    private final List<String> registered = new ArrayList<>();

    public MediaScheduling(MediaRuntimeMode mode, int workerConcurrency) {
        this.mode = mode;
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(4);
        this.scheduler.setThreadNamePrefix("media-");
        this.scheduler.setWaitForTasksToCompleteOnShutdown(false);
        this.scheduler.initialize();
        AtomicInteger workers = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "media-worker-" + workers.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.workerExecutor = new ThreadPoolExecutor(workerConcurrency, workerConcurrency, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    /** The sweeper, probes, deletion job and full listing: scheduled unless every adapter is {@code none}. */
    public synchronized void scheduleUnlessEveryAdapterNone(String name, Runnable task, Duration delay) {
        if (mode.everyAdapterNone()) {
            return;
        }
        scheduler.scheduleWithFixedDelay(task, delay);
        registered.add(name);
    }

    /** As {@link #scheduleUnlessEveryAdapterNone(String, Runnable, Duration)}, the first run after the start delay. */
    public synchronized void scheduleUnlessEveryAdapterNone(String name, Runnable task, Duration startDelay,
                                                            Duration delay) {
        if (mode.everyAdapterNone()) {
            return;
        }
        scheduler.scheduleWithFixedDelay(task, java.time.Instant.now().plus(startDelay), delay);
        registered.add(name);
    }

    /** A one-time run soon after start (the start-up sweep), unless every adapter is {@code none}. */
    public synchronized void runOnceUnlessEveryAdapterNone(String name, Runnable task) {
        if (mode.everyAdapterNone()) {
            return;
        }
        scheduler.execute(task);
        registered.add(name);
    }

    /** One later run on the media scheduler (a retry of the processor self-probe). */
    public void runOnceAfter(Runnable task, Duration delay) {
        scheduler.schedule(task, java.time.Instant.now().plus(delay));
    }

    /** The processing worker: scheduled only while processing is effectively on. */
    public synchronized void scheduleWorker(String name, Runnable task, Duration delay) {
        if (!mode.processingOn()) {
            return;
        }
        scheduler.scheduleWithFixedDelay(task, delay);
        registered.add(name);
    }

    /** Whether processing is effectively on for this instance (the worker is scheduled only then). */
    public boolean processingOn() {
        return mode.processingOn();
    }

    /** The bounded executor of processing runs; the worker claims only for a free thread. */
    public ExecutorService workerExecutor() {
        return workerExecutor;
    }

    /** Free worker threads right now. */
    public int freeWorkerThreads() {
        return workerExecutor.getMaximumPoolSize() - workerExecutor.getActiveCount();
    }

    /** The names of the scheduled tasks, in registration order. */
    public synchronized List<String> registeredTasks() {
        return List.copyOf(registered);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        workerExecutor.shutdownNow();
    }
}
