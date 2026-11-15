package vn.sunvibe.config.web;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import vn.sunvibe.shared.web.MediaContentPaths;

/**
 * Open-in-view for every endpoint except the two survey photo content streams (survey-media spec A-13) and the endpoints
 * E05 to E07 add (ADR-012 decision 8: each path pattern is listed in this class, with a test, in the change that adds
 * the endpoint). With {@code spring.jpa.open-in-view=false} this interceptor gives every other endpoint today's
 * behaviour, while no EntityManager, and so no pooled connection, is bound while a content body or variant streams, and
 * an excluded read loads everything it returns inside its own transaction. Turning open-in-view off for the whole
 * server is a separate decision.
 */
@Configuration(proxyBeanMethods = false)
public class OpenInViewExceptMediaContentConfig implements WebMvcConfigurer {

	/** The brokerage agreement eligibility read of a lead (E05-S1 spec S1-D8). */
	static final String BROKERAGE_ELIGIBILITY_PATTERN = "/api/v1/staff/owner-leads/*/brokerage-eligibility";

	/** The brokerage agreement create (E05-S2 spec S2-D13). */
	static final String BROKERAGE_AGREEMENTS_PATTERN = "/api/v1/staff/brokerage-agreements";

	/** The brokerage agreement detail read (E05-S2 spec S2-D13). */
	static final String BROKERAGE_AGREEMENT_PATTERN = "/api/v1/staff/brokerage-agreements/*";

	private final EntityManagerFactory entityManagerFactory;

	public OpenInViewExceptMediaContentConfig(EntityManagerFactory entityManagerFactory) {
		this.entityManagerFactory = entityManagerFactory;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		OpenEntityManagerInViewInterceptor openInView = new OpenEntityManagerInViewInterceptor();
		if (entityManagerFactory != null) {
			openInView.setEntityManagerFactory(entityManagerFactory);
		}
		registry.addWebRequestInterceptor(openInView)
				.excludePathPatterns(MediaContentPaths.UPLOAD_PATTERN, MediaContentPaths.READ_PATTERN,
						BROKERAGE_ELIGIBILITY_PATTERN, BROKERAGE_AGREEMENTS_PATTERN, BROKERAGE_AGREEMENT_PATTERN);
	}
}
