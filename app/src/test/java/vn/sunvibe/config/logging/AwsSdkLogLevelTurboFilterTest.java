package vn.sunvibe.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * The AWS SDK and HTTP wire loggers stay at WARN whatever level is asked for (survey-media spec A-13; plan PD-3;
 * SM-15 logs): no key, bucket, header or credential reaches a log.
 */
class AwsSdkLogLevelTurboFilterTest {

	private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
	private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void attach() {
		LogFilterRegistrar.install(context);
		appender.setContext(context);
		appender.start();
		context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
	}

	@AfterEach
	void detach() {
		context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(appender);
		for (String name : List.of("software.amazon.awssdk", "org.apache.http.wire", "vn.sunvibe.sample")) {
			loggingSystem.setLogLevel(name, null);
		}
	}

	@Test
	void sdkLoggerSetToDebug_emitsNothingBelowWarn() {
		loggingSystem.setLogLevel("software.amazon.awssdk", LogLevel.TRACE);
		loggingSystem.setLogLevel("org.apache.http.wire", LogLevel.TRACE);

		for (String name : List.of("software.amazon.awssdk.request", "software.amazon.awssdk.requestId",
				"software.amazon.awssdk.auth.signer", "org.apache.http.wire", "org.apache.http.headers",
				"org.apache.hc.client5.http.wire", "org.apache.hc.client5.http.headers")) {
			Logger logger = context.getLogger(name);
			logger.debug("Authorization: AWS4-HMAC-SHA256 Credential=secret");
			logger.info("bucket media-bucket key media/x/1/FULL");
			logger.trace("wire bytes");
		}
		Logger sdk = context.getLogger("software.amazon.awssdk.request");
		sdk.warn("sdk warning kept");

		assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly("sdk warning kept");
	}

	@Test
	void otherLoggers_unaffected() {
		loggingSystem.setLogLevel("vn.sunvibe.sample", LogLevel.DEBUG);

		context.getLogger("vn.sunvibe.sample").debug("ours at debug");
		context.getLogger("software.amazonx.other").info("not the sdk");

		assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
				.containsExactly("ours at debug", "not the sdk");
	}

	@Test
	void registrar_isListedForEveryApplication() {
		assertThat(org.springframework.core.io.support.SpringFactoriesLoader.forDefaultResourceLocation()
				.load(org.springframework.context.ApplicationListener.class))
				.anyMatch(LogFilterRegistrar.class::isInstance);
	}

	@Test
	void filterPresent_afterContextRefresh() {
		LoggerContext fresh = new LoggerContext();
		LogFilterRegistrar.install(fresh);
		ListAppender<ILoggingEvent> freshAppender = new ListAppender<>();

		fresh.reset();
		freshAppender.setContext(fresh);
		freshAppender.start();
		Logger root = fresh.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		root.addAppender(freshAppender);
		root.setLevel(ch.qos.logback.classic.Level.TRACE);
		fresh.getLogger("software.amazon.awssdk.request").debug("after reset");
		fresh.reset();
		root.addAppender(freshAppender);
		fresh.getLogger("software.amazon.awssdk.request").info("after a second reset");

		assertThat(fresh.getTurboFilterList()).anyMatch(AwsSdkLogLevelTurboFilter.class::isInstance);
		assertThat(freshAppender.list).isEmpty();
	}
}
