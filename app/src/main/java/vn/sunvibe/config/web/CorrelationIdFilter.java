package vn.sunvibe.config.web;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.sunvibe.shared.generator.id.UlidGenerator;
import vn.sunvibe.shared.logging.CorrelationId;
import vn.sunvibe.shared.logging.RequestId;

/**
 * Gives every request a correlation id (ADR-010): a valid client-sent {@code X-Correlation-Id} is kept, anything else
 * is replaced by a new ULID. The id goes to the log context and to the response header before the request is handled,
 * so every error body and log line of the request carries it, beside a server request id (survey-media plan PD-14).
 * Runs first, ahead of security and rate limiting.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final UlidGenerator ulidGenerator = new UlidGenerator();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationId.HEADER);
        String id = CorrelationId.isValid(incoming) ? incoming : ulidGenerator.generateId();
        MDC.put(CorrelationId.MDC_KEY, id);
        // survey-media plan PD-14, SV-052: a server id of this request, whatever id the client chose
        MDC.put(RequestId.MDC_KEY, ulidGenerator.generateId());
        response.setHeader(CorrelationId.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
            MDC.remove(RequestId.MDC_KEY);
        }
    }
}
