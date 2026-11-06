package vn.sunvibe.contract;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import vn.sunvibe.contract.WireContract.Fault;

/**
 * Checks each answer of {@code /api/**} against the contract, before the rate limiter and the security chain so that their
 * answers count too (contract-wire spec 4.3). The error dispatch carries the answers of {@code sendError} and of a thrown
 * exception, under the original path. A broken check is a fault in the ledger: the client keeps its answer.
 */
final class WireContractFilter extends OncePerRequestFilter implements Ordered {

	private static final String API = "/api/";

	private final Supplier<WireContract> contract;
	private final WireLedger ledger;

	WireContractFilter() {
		this(WireContract::shared, WireLedger.shared());
	}

	WireContractFilter(Supplier<WireContract> contract, WireLedger ledger) {
		this.contract = contract;
		this.ledger = ledger;
	}

	@Override
	public int getOrder() {
		return HIGHEST_PRECEDENCE;
	}

	@Override
	protected boolean shouldNotFilterErrorDispatch() {
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String uri = pathOf(request);
		if (!uri.startsWith(API) || WireContract.isBinaryRead(request.getMethod(), uri)) {
			chain.doFilter(request, response);
			return;
		}
		ContentCachingResponseWrapper answer = new ContentCachingResponseWrapper(response);
		try {
			chain.doFilter(request, answer);
			if (!endedInSendError(request, answer)) {
				record(request.getMethod(), uri, answer);
			}
		}
		finally {
			answer.copyBodyToResponse();
		}
	}

	private static String pathOf(HttpServletRequest request) {
		Object original = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
		return original instanceof String path ? path : request.getRequestURI();
	}

	/** The body stays in this wrapper until the end, so only {@code sendError} commits a request pass. */
	private static boolean endedInSendError(HttpServletRequest request, HttpServletResponse answer) {
		return request.getDispatcherType() == DispatcherType.REQUEST && answer.isCommitted();
	}

	private void record(String method, String uri, ContentCachingResponseWrapper answer) {
		int status = answer.getStatus();
		byte[] body = answer.getContentAsByteArray();
		String template = uri;
		List<Fault> faults;
		try {
			WireContract wire = contract.get();
			template = wire.templateOf(method, uri);
			faults = wire.check(method, uri, status, answer.getContentType(), body);
		}
		catch (RuntimeException ex) {
			faults = List.of(new Fault(method, template, status, "", "check-failed"));
		}
		ledger.record(method, template, status, faults, body);
	}
}
