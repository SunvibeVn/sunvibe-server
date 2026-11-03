package vn.sunvibe.config.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;

/**
 * Open-in-view stays for every endpoint except the two media content streams (survey-media spec A-13; plan Task A12):
 * no EntityManager, and so no pooled connection, is bound while their bytes move; the server-wide switch is a
 * separate decision.
 */
class OpenInViewExceptMediaContentConfigTest {

	private final List<Object> interceptors = registered();

	@Test
	void contentPaths_haveNoEntityManagerBound() {
		MappedInterceptor openInView = openInView();

		assertFalse(openInView.matches(request("PUT", "/api/v1/staff/survey-media/01JMEDIA0000000000000000AA/content")));
		assertFalse(openInView.matches(request("GET",
				"/api/v1/staff/survey-media/01JMEDIA0000000000000000AA/content/thumbnail")));
	}

	@Test
	void otherPaths_keepOpenInView() {
		MappedInterceptor openInView = openInView();

		for (String path : List.of("/api/v1/staff/owner-leads", "/api/v1/staff/survey-checklists/x/media",
				"/api/v1/staff/survey-media/01JMEDIA0000000000000000AA/removal", "/api/v1/me")) {
			assertTrue(openInView.matches(request("GET", path)), path);
		}
	}

	private MappedInterceptor openInView() {
		assertEquals(1, interceptors.size());
		MappedInterceptor mapped = assertInstanceOf(MappedInterceptor.class, interceptors.get(0));
		assertInstanceOf(WebRequestHandlerInterceptorAdapter.class, mapped.getInterceptor());
		return mapped;
	}

	private static MockHttpServletRequest request(String method, String path) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRequestURI(path);
		org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
		return request;
	}

	private static List<Object> registered() {
		ExposedRegistry registry = new ExposedRegistry();
		new OpenInViewExceptMediaContentConfig(null).addInterceptors(registry);
		return registry.all();
	}

	/** Makes the registered interceptors visible to the test. */
	private static final class ExposedRegistry extends InterceptorRegistry {
		List<Object> all() {
			return getInterceptors();
		}
	}
}
