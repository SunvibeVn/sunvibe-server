package vn.sunvibe.brokerage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

import vn.sunvibe.brokerage.core.presentation.controller.BrokerageEligibilityController;

/**
 * The brokerage eligibility read runs without open-in-view, while the intake block keeps it (E05-S1 spec S1-D8, EL-11;
 * ADR-012 decision 8; plan decision E05S1-P2): asked of the handler mapping of the running context, so the interceptor
 * Spring applies is the one checked. The H2 test profile and the annotations of {@code SurveyOpenApiTest}, so no
 * database is needed and its context is reused.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrokerageOpenInViewTest {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void handlerChain_eligibilityPath_noOpenInView_intakeBlockKeepsIt() throws Exception {
		HandlerExecutionChain eligibility = chain("/api/v1/staff/owner-leads/01JLEAD000000000000000000A/brokerage-eligibility");
		HandlerMethod handler = assertInstanceOf(HandlerMethod.class, eligibility.getHandler());
		assertEquals(BrokerageEligibilityController.class, handler.getBeanType());
		assertEquals("eligibility", handler.getMethod().getName());
		assertEquals(0, openInViewInterceptors(eligibility));

		HandlerExecutionChain intakeBlock = chain("/api/v1/staff/owner-leads/01JLEAD000000000000000000A/inventory-intake");
		assertEquals(1, openInViewInterceptors(intakeBlock));
	}

	private HandlerExecutionChain chain(String path) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setRequestURI(path);
		ServletRequestPathUtils.parseAndCache(request);
		HandlerExecutionChain chain = handlerMapping.getHandler(request);
		assertNotNull(chain, path);
		return chain;
	}

	private static long openInViewInterceptors(HandlerExecutionChain chain) {
		return chain.getInterceptorList().stream().filter(WebRequestHandlerInterceptorAdapter.class::isInstance).count();
	}
}
