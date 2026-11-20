package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import vn.sunvibe.listing.core.presentation.controller.ListingController;

/**
 * The seven revision paths run without open-in-view (listing-publish E06-S2 spec E06S2-D16, RD-12; ADR-012 decision
 * 8): asked of the handler mapping of the running context. The annotations of {@link ListingOpenInViewTest}, which
 * stays untouched (plan E06S2-P1), so its context is reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingRevisionOpenInViewTest {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void handlerChain_revisionPaths_noOpenInView() throws Exception {
		String listing = "/api/v1/staff/listings/01JGLISTING000000000000000";
		Map<String, String> handlers = Map.of(
				"GET " + listing + "/revisions/1", "revision",
				"POST " + listing + "/revisions", "startRevision",
				"PUT " + listing + "/revisions/1/content", "saveContent",
				"PUT " + listing + "/revisions/1/terms", "captureTerms",
				"POST " + listing + "/revisions/1/submission", "submit",
				"POST " + listing + "/revisions/1/withdrawal", "withdraw",
				"GET " + listing + "/terms-sources", "termsSources");
		for (Map.Entry<String, String> expected : handlers.entrySet()) {
			String[] request = expected.getKey().split(" ");
			HandlerExecutionChain chain = chain(request[0], request[1]);
			HandlerMethod handler = assertInstanceOf(HandlerMethod.class, chain.getHandler());
			assertEquals(ListingController.class, handler.getBeanType(), expected.getKey());
			assertEquals(expected.getValue(), handler.getMethod().getName(), expected.getKey());
			assertEquals(0, openInViewInterceptors(chain), expected.getKey());
		}

		HandlerExecutionChain intakeBlock = chain("GET",
				"/api/v1/staff/owner-leads/01JLEAD000000000000000000A/inventory-intake");
		assertEquals(1, openInViewInterceptors(intakeBlock));
	}

	private HandlerExecutionChain chain(String method, String path) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRequestURI(path);
		ServletRequestPathUtils.parseAndCache(request);
		HandlerExecutionChain chain = handlerMapping.getHandler(request);
		assertNotNull(chain, method + " " + path);
		return chain;
	}

	private static long openInViewInterceptors(HandlerExecutionChain chain) {
		return chain.getInterceptorList().stream().filter(WebRequestHandlerInterceptorAdapter.class::isInstance).count();
	}
}
