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

	@Test
	void intakeBlock_keepsOpenInView() {
		MappedInterceptor openInView = openInView();

		assertTrue(openInView.matches(request("GET",
				"/api/v1/staff/owner-leads/01JLEAD000000000000000000A/inventory-intake")));
	}

	/** E05-S1 spec S1-D8, ADR-012 decision 8: the brokerage eligibility read runs without open-in-view. */
	@Test
	void eligibilityPath_hasNoEntityManagerBound() {
		MappedInterceptor openInView = openInView();

		assertFalse(openInView.matches(request("GET",
				"/api/v1/staff/owner-leads/01JLEAD000000000000000000A/brokerage-eligibility")));
	}

	/** E05-S2 spec S2-D13, ADR-012 decision 8: the agreement create and detail run without open-in-view. */
	@Test
	void agreementPaths_haveNoEntityManagerBound() {
		MappedInterceptor openInView = openInView();

		assertFalse(openInView.matches(request("POST", "/api/v1/staff/brokerage-agreements")));
		assertFalse(openInView.matches(request("GET", "/api/v1/staff/brokerage-agreements/01JAGREEMENT00000000000000")));
		assertFalse(openInView.matches(request("GET",
				"/api/v1/staff/owner-leads/01JLEAD000000000000000000A/brokerage-eligibility")));
	}

	/** E05-S3 spec S3-D16, ADR-012 decision 8: the draft write and the survey reference run without open-in-view. */
	@Test
	void draftAndSurveyTermsPaths_haveNoEntityManagerBound() {
		MappedInterceptor openInView = openInView();

		assertFalse(openInView.matches(request("PUT", "/api/v1/staff/brokerage-agreements/01JAGREEMENT00000000000000/draft")));
		assertFalse(openInView.matches(request("GET",
				"/api/v1/staff/brokerage-agreements/01JAGREEMENT00000000000000/survey-terms")));
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
