package vn.sunvibe.listing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

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

import vn.sunvibe.listing.core.presentation.controller.ListingPublicController;
import vn.sunvibe.listing.core.presentation.controller.ListingPublicationController;

/**
 * The anonymous public read and the staff preview run without open-in-view (listing-publish E06-S7 spec E06S7-D8, D9;
 * criterion PB-14; ADR-012 decision 8): asked of the handler mapping of the running context. The annotations of
 * {@link ListingPublicationOpenInViewTest}, so its context is reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingPublicOpenInViewTest {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void handlerChain_publicRead_noOpenInView() throws Exception {
		HandlerExecutionChain chain = chain("GET", "/api/v1/public/listings/TD-2345-6789");
		HandlerMethod handler = assertInstanceOf(HandlerMethod.class, chain.getHandler());

		assertEquals(ListingPublicController.class, handler.getBeanType());
		assertEquals("publicListing", handler.getMethod().getName());
		assertEquals(0, openInViewInterceptors(chain));

		// Task A9: the staff preview of a revision's public data, under the listing sub-resources pattern
		HandlerExecutionChain preview = chain("GET",
				"/api/v1/staff/listings/01JGLISTING000000000000000/revisions/2/public-preview");
		HandlerMethod previewHandler = assertInstanceOf(HandlerMethod.class, preview.getHandler());
		assertEquals(List.of(ListingPublicationController.class.getName(), "publicPreview", "0"),
				List.of(previewHandler.getBeanType().getName(), previewHandler.getMethod().getName(),
						String.valueOf(openInViewInterceptors(preview))));

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
