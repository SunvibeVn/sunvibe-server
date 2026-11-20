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
 * The sub-resources of one listing run without open-in-view (listing-publish E06-S2 spec E06S2-D16, RD-12; ADR-012
 * decision 8). The new case beside {@link OpenInViewExceptMediaContentConfigTest}, which stays untouched (plan
 * E06S2-P1): its field {@code interceptors} (:23) and its helpers {@code openInView}, {@code request},
 * {@code registered} and {@code ExposedRegistry} (:185-210) are copied as written.
 */
class OpenInViewListingSubresourcesConfigTest {

	private final List<Object> interceptors = registered();

	@Test
	void listingSubresources_haveNoEntityManagerBound() {
		MappedInterceptor openInView = openInView();
		String revisions = "/api/v1/staff/listings/01JGLISTING000000000000000/revisions";

		assertEquals("/api/v1/staff/listings/*/**", OpenInViewExceptMediaContentConfig.LISTING_SUBRESOURCES_PATTERN);
		assertFalse(openInView.matches(request("GET", revisions + "/1")));
		assertFalse(openInView.matches(request("POST", revisions)));
		assertFalse(openInView.matches(request("PUT", revisions + "/1/content")));
		assertFalse(openInView.matches(request("PUT", revisions + "/1/terms")));
		assertFalse(openInView.matches(request("POST", revisions + "/1/submission")));
		assertFalse(openInView.matches(request("POST", revisions + "/1/withdrawal")));
		assertFalse(openInView.matches(request("GET",
				"/api/v1/staff/listings/01JGLISTING000000000000000/terms-sources")));
		assertTrue(openInView.matches(request("GET",
				"/api/v1/staff/owner-leads/01JLEAD000000000000000000A/inventory-intake")));
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
