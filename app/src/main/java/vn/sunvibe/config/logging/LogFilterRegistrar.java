package vn.sunvibe.config.logging;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;

/**
 * Adds {@link AwsSdkLogLevelTurboFilter} to the logback context once the application is prepared (after Boot's
 * logging initialisation), and again after every reset of the logging system (survey-media plan PD-3). Listed in
 * {@code META-INF/spring.factories}; no {@code logback-spring.xml} is needed.
 */
public class LogFilterRegistrar implements ApplicationListener<ApplicationPreparedEvent> {

	@Override
	public void onApplicationEvent(ApplicationPreparedEvent event) {
		ILoggerFactory factory = LoggerFactory.getILoggerFactory();
		if (factory instanceof LoggerContext context) {
			install(context);
		}
	}

	/** Adds the filter unless present, and a reset-resistant listener that adds it again after each reset. */
	public static synchronized void install(LoggerContext context) {
		addFilter(context);
		if (context.getCopyOfListenerList().stream().noneMatch(ReinstallOnReset.class::isInstance)) {
			context.addListener(new ReinstallOnReset());
		}
	}

	private static void addFilter(LoggerContext context) {
		if (context.getTurboFilterList().stream().noneMatch(AwsSdkLogLevelTurboFilter.class::isInstance)) {
			AwsSdkLogLevelTurboFilter filter = new AwsSdkLogLevelTurboFilter();
			filter.setContext(context);
			filter.start();
			context.addTurboFilter(filter);
		}
	}

	/** Puts the filter back after a reset, which clears the turbo filters. */
	private static final class ReinstallOnReset implements LoggerContextListener {

		@Override
		public boolean isResetResistant() {
			return true;
		}

		@Override
		public void onStart(LoggerContext context) {
			addFilter(context);
		}

		@Override
		public void onReset(LoggerContext context) {
			addFilter(context);
		}

		@Override
		public void onStop(LoggerContext context) {
		}

		@Override
		public void onLevelChange(Logger logger, Level level) {
		}
	}
}
