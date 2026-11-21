package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.LinkedHashMap;
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
import vn.sunvibe.listing.core.presentation.controller.ListingReviewController;

/**
 * The six review operations run without open-in-view (listing-publish E06-S4 spec RV-14; ADR-012 decision 8): asked of
 * the handler mapping of the running context; the intake path keeps its interceptor. The annotations of
 * {@link ListingRevisionOpenInViewTest}, so its context is reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingReviewOpenInViewTest {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void handlerChain_reviewPaths_noOpenInView() throws Exception {
		String listings = "/api/v1/staff/listings";
		String revision = listings + "/01JGLISTING000000000000000/revisions/1";
		Map<String, String> handlers = new LinkedHashMap<>();
		String review = ListingReviewController.class.getSimpleName();
		handlers.put("GET " + listings + "/review-queue", review + ".queue");
		handlers.put("GET " + revision + "/review", review + ".review");
		handlers.put("POST " + revision + "/decision", review + ".decide");
		handlers.put("GET " + revision + "/decision", review + ".decision");
		handlers.put("POST " + revision + "/unit-location", review + ".openUnitLocation");
		handlers.put("POST " + revision + "/copies", ListingController.class.getSimpleName() + ".copy");
		for (Map.Entry<String, String> expected : handlers.entrySet()) {
			String[] request = expected.getKey().split(" ");
			HandlerExecutionChain chain = chain(request[0], request[1]);
			HandlerMethod handler = assertInstanceOf(HandlerMethod.class, chain.getHandler());
			assertEquals(expected.getValue(), handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName(),
					expected.getKey());
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
