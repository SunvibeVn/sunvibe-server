package vn.sunvibe.listing.core.presentation.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.listing.core.domain.model.listing.ListingCode;
import vn.sunvibe.listing.core.presentation.controller.ListingPublicController;
import vn.sunvibe.listing.core.presentation.exception.ListingExceptionHandler;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The edge of the anonymous public read (listing-publish E06-S7 spec E06S7-D7, D8; ADR-012 decision 4). For a GET of
 * {@link ListingPublicController#PATH} only, it sets {@code Cache-Control: no-store} before the chain runs, so every
 * answer of the path carries it, whoever writes it later; and while the switch {@code app.listing.public-read.enabled}
 * is off (the default), or the code is not of the code's form ({@link ListingCode#isWellFormed}), it writes the one 404
 * itself, the body of a hidden listing, and does not call the chain: no controller, transaction or connection is
 * reached. The switch is read once, at start-up.
 * <p>
 * Runs after the correlation id filter, the rate limiter (which counts these requests, so a burst gets its 429) and
 * Spring Security, as {@code OwnerIntakeEnabledFilter} does, so the 404 carries the correlation id and the CORS headers.
 */
@Component
@Order(-1)
public class ListingPublicReadFilter extends OncePerRequestFilter {

    private static final PathPattern PUBLIC_PATH = PathPatternParser.defaultInstance.parse(ListingPublicController.PATH);
    private static final String CODE = "code";

    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public ListingPublicReadFilter(ObjectMapper objectMapper,
                                   @Value("${app.listing.public-read.enabled:false}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /** GET only, and only the public path as MVC and Security match it. */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod()) || !PUBLIC_PATH.matches(pathOf(request));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (enabled && ListingCode.isWellFormed(codeOf(request))) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.NOT_FOUND,
                ListingExceptionHandler.NOT_FOUND_MESSAGE, List.of())));
    }

    /**
     * Path segments decoded as UTF-8 and matrix parameters dropped, as Spring MVC and Spring Security read them; the
     * request's character encoding comes from the client and is not used.
     */
    private static PathContainer pathOf(HttpServletRequest request) {
        return RequestPath.parse(request.getRequestURI(), request.getContextPath()).pathWithinApplication();
    }

    /** The code as the controller receives it: the decoded value of the matched segment. */
    private static String codeOf(HttpServletRequest request) {
        PathPattern.PathMatchInfo match = PUBLIC_PATH.matchAndExtract(pathOf(request));
        return match == null ? null : match.getUriVariables().get(CODE);
    }
}
