package vn.sunvibe.config.logging;

import java.util.List;

import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Denies every event below WARN of the AWS SDK and HTTP wire loggers, whatever level configuration or a runtime call
 * asks for (survey-media spec A-13; plan PD-3), so no key, bucket, header or credential is ever logged.
 */
public class AwsSdkLogLevelTurboFilter extends TurboFilter {

	/** The SDK (its request and request-id loggers included) and the wire and header loggers of its HTTP clients. */
	static final List<String> CAPPED = List.of("software.amazon.awssdk", "org.apache.http.wire",
			"org.apache.http.headers", "org.apache.hc.client5.http.wire", "org.apache.hc.client5.http.headers");

	@Override
	public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
			Throwable throwable) {
		if (level == null || level.isGreaterOrEqual(Level.WARN)) {
			return FilterReply.NEUTRAL;
		}
		String name = logger.getName();
		for (String capped : CAPPED) {
			if (name.equals(capped) || name.startsWith(capped + ".")) {
				return FilterReply.DENY;
			}
		}
		return FilterReply.NEUTRAL;
	}
}
