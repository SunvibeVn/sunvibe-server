package vn.sunvibe.auth.core.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import vn.sunvibe.auth.core.application.shared.external.audit.AuditLogPort;
import vn.sunvibe.auth.core.application.shared.external.email.AfterCommitMailPort;
import vn.sunvibe.auth.core.application.usecase.auth.EmailNotificationDeliveryUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.ExpiredRefreshTokenPurgeUseCase;
import vn.sunvibe.auth.core.application.usecase.auth.RegistrationChallengePurgeUseCase;
import vn.sunvibe.auth.core.application.usecase.user.PendingAccountCleanupUseCase;

/**
 * The auth jobs are scheduled by the module itself: in a context with only auth beans, every job of the scheduling
 * package is registered. The package is scanned, not listed, so a job that lost {@code @Component} fails here too.
 * The {@code @Scheduled} eviction of {@code TokenBucketRateLimiter} is no job and stays out.
 */
class AuthSchedulingConfigTest {

	private static final List<String> JOBS = List.of(
			"AuditLogCleanupJob.purgeOldAuditEvents",
			"EmailNotificationJob.deliverDueNotices",
			"PendingAccountCleanupJob.deactivateExpiredPendingAccounts",
			"RefreshTokenCleanupJob.purgeExpiredRefreshTokens",
			"RegistrationChallengeCleanupJob.purgeExpiredRegistrationChallenges");

	@Test
	void context_withOnlyAuthBeans_registersTheFiveJobs() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			// AuditLogCleanupJob reads its retention as a Duration ("180d"), as Boot converts it
			context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
			context.registerBean(AuditLogPort.class, () -> unused(AuditLogPort.class));
			context.registerBean(AfterCommitMailPort.class, () -> unused(AfterCommitMailPort.class));
			context.registerBean(EmailNotificationDeliveryUseCase.class, () -> unused(EmailNotificationDeliveryUseCase.class));
			context.registerBean(PendingAccountCleanupUseCase.class, () -> unused(PendingAccountCleanupUseCase.class));
			context.registerBean(ExpiredRefreshTokenPurgeUseCase.class, () -> unused(ExpiredRefreshTokenPurgeUseCase.class));
			context.registerBean(RegistrationChallengePurgeUseCase.class,
					() -> unused(RegistrationChallengePurgeUseCase.class));
			context.register(AuthSchedulingConfig.class);
			context.scan("vn.sunvibe.auth.core.infrastructure.scheduling");
			context.refresh();

			List<String> tasks = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
					.flatMap(holder -> holder.getScheduledTasks().stream())
					.map(ScheduledTask::toString)
					.toList();

			for (String job : JOBS) {
				assertTrue(tasks.stream().anyMatch(task -> task.endsWith(".scheduling." + job)), job + " in " + tasks);
			}
		}
	}

	/** A dependency the jobs never call here: no schedule fires while the context is open. */
	private static <T> T unused(Class<T> type) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				return switch (method.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> type.getSimpleName() + " stub";
				};
			}
			throw new UnsupportedOperationException(method.getName() + " is not called by this test");
		}));
	}
}
