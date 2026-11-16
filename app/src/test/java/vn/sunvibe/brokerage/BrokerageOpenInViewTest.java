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

import vn.sunvibe.brokerage.core.presentation.controller.BrokerageAgreementController;
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

	/** E05-S2 spec S2-D13, AD-11: the agreement create and detail run without open-in-view. */
	@Test
	void handlerChain_agreementPaths_noOpenInView() throws Exception {
		HandlerExecutionChain create = chain("POST", "/api/v1/staff/brokerage-agreements");
		HandlerMethod createHandler = assertInstanceOf(HandlerMethod.class, create.getHandler());
		assertEquals(BrokerageAgreementController.class, createHandler.getBeanType());
		assertEquals("create", createHandler.getMethod().getName());
		assertEquals(0, openInViewInterceptors(create));

		HandlerExecutionChain detail = chain("GET", "/api/v1/staff/brokerage-agreements/01JAGREEMENT00000000000000");
		HandlerMethod detailHandler = assertInstanceOf(HandlerMethod.class, detail.getHandler());
		assertEquals(BrokerageAgreementController.class, detailHandler.getBeanType());
		assertEquals("detail", detailHandler.getMethod().getName());
		assertEquals(0, openInViewInterceptors(detail));

		HandlerExecutionChain intakeBlock = chain("/api/v1/staff/owner-leads/01JLEAD000000000000000000A/inventory-intake");
		assertEquals(1, openInViewInterceptors(intakeBlock));
	}

	/** E05-S3 spec S3-D16, ED-13: the draft write and the survey reference run without open-in-view. */
	@Test
	void handlerChain_draftAndSurveyTermsPaths_noOpenInView() throws Exception {
		HandlerExecutionChain draft = chain("PUT", "/api/v1/staff/brokerage-agreements/01JAGREEMENT00000000000000/draft");
		HandlerMethod draftHandler = assertInstanceOf(HandlerMethod.class, draft.getHandler());
		assertEquals(BrokerageAgreementController.class, draftHandler.getBeanType());
		assertEquals("updateDraft", draftHandler.getMethod().getName());
		assertEquals(0, openInViewInterceptors(draft));

		HandlerExecutionChain surveyTerms = chain("GET",
				"/api/v1/staff/brokerage-agreements/01JAGREEMENT00000000000000/survey-terms");
		HandlerMethod surveyHandler = assertInstanceOf(HandlerMethod.class, surveyTerms.getHandler());
		assertEquals(BrokerageAgreementController.class, surveyHandler.getBeanType());
		assertEquals("surveyTerms", surveyHandler.getMethod().getName());
		assertEquals(0, openInViewInterceptors(surveyTerms));

		HandlerExecutionChain intakeBlock = chain("/api/v1/staff/owner-leads/01JLEAD000000000000000000A/inventory-intake");
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
