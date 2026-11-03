package vn.sunvibe.config.web;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import vn.sunvibe.shared.web.MediaContentPaths;

/**
 * Open-in-view for every endpoint except the two survey photo content streams (survey-media spec A-13): with
 * {@code spring.jpa.open-in-view=false} this interceptor gives every other endpoint today's behaviour, while no
 * EntityManager, and so no pooled connection, is bound while a content body or variant streams. Turning open-in-view
 * off for the whole server is a separate decision.
 */
@Configuration(proxyBeanMethods = false)
public class OpenInViewExceptMediaContentConfig implements WebMvcConfigurer {

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
				.excludePathPatterns(MediaContentPaths.UPLOAD_PATTERN, MediaContentPaths.READ_PATTERN);
	}
}
