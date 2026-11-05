package vn.sunvibe.auth.core.infrastructure.email;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * The small pool that sends auth mail off the request thread (auth-email-access PD-7): 2 threads, a queue of 100. A
 * holder and not an executor bean, as in {@code MediaScheduling}: a bean of an executor type would switch off Boot's
 * default one. Spring shuts it down.
 */
@Log4j2
@Component("authMailExecutor")
public class AuthMailExecutor implements DisposableBean {

    private static final int THREADS = 2;
    private static final int QUEUE_CAPACITY = 100;
    private static final int SHUTDOWN_WAIT_SECONDS = 5;

    private final ThreadPoolTaskExecutor executor;

    public AuthMailExecutor() {
        this(THREADS, QUEUE_CAPACITY);
    }

    AuthMailExecutor(int threads, int queueCapacity) {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("auth-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        executor.initialize();
    }

    /** @return false if the queue was full, which is logged */
    public boolean submit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (TaskRejectedException e) {
            log.warn("auth.mail.queue-full");
            return false;
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
