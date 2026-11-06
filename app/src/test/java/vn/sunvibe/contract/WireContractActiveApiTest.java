package vn.sunvibe.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The check is alive over a real HTTP stack (contract-wire spec S-11): losing the registration of the filter or of the
 * extension would leave every build green with no protection, so that loss fails here. The two answers only an error
 * dispatch carries count once: a request pass that also recorded would count them twice.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WireContractActiveApiTest {

	private static final String THROWS = "/api/v1/wire-contract-test/throws";

	@Value("${local.server.port}")
	private int port;

	@Test
	void check_sawARealAnswer() throws Exception {
		assertEquals(200, get("/api/v1/public/provinces"));

		assertTrue(WireLedger.shared().seen("GET", "/api/v1/public/provinces", 200));
	}

	/** The firewall refuses a path parameter with sendError, so only the error dispatch carries this answer. */
	@Test
	void errorDispatch_isSeen() throws Exception {
		assertEquals(400, get("/api/v1/public/provinces;x=1"));

		assertEquals(1, WireLedger.shared().answers("GET", "/api/v1/public/provinces;x=1", 400));
	}

	/** Nothing is recorded on the failing pass; the container's error dispatch answers, under the original path. */
	@Test
	void exceptionInTheChain_isSeenOnItsErrorDispatch() throws Exception {
		assertEquals(500, get(THROWS));

		assertEquals(1, WireLedger.shared().answers("GET", THROWS, 500));
	}

	@Test
	void extension_ran() {
		assertTrue(WireContractExtension.ran());
	}

	private int get(String path) throws Exception {
		try (HttpClient http = HttpClient.newHttpClient()) {
			return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
					HttpResponse.BodyHandlers.discarding()).statusCode();
		}
	}

	/** A failure past the wire filter that no handler catches, so it reaches the container. */
	@TestConfiguration
	static class ThrowingPath {

		@Bean
		FilterRegistrationBean<OncePerRequestFilter> throwingFilter() {
			FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(new OncePerRequestFilter() {
				@Override
				protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
						throws ServletException, IOException {
					if (THROWS.equals(request.getRequestURI())) {
						throw new ServletException("Thrown past every handler");
					}
					chain.doFilter(request, response);
				}
			});
			registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
			return registration;
		}
	}
}
