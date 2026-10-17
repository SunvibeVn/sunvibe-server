package vn.sunvibe.acquisition.core.presentation.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.ObjectMapper;
import vn.sunvibe.acquisition.core.presentation.controller.OwnerIntakeController;
import vn.sunvibe.acquisition.core.presentation.exception.AcquisitionExceptionHandler;
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The server switch of the public intake form (acquisition-owner-intake BR-15, spec C-7): the form is not open to
 * real owners until the staff queue UI exists and the consent text has passed legal review (acquisition-owner-intake Q-11). While
 * {@code app.acquisition.owner-intake.enabled} is false (the default) every request to the form path that reaches
 * this filter (Spring Security lets only the POST through anonymously) is answered 404 before it reaches the
 * application, so no personal data is accepted or stored; the ward catalog stays open. The page flag of the web app
 * only hides the page.
 * <p>
 * Runs after the correlation id filter, the rate limiter (which still counts these requests, so a burst gets its 429)
 * and Spring Security, so the 404 carries the correlation id and the CORS headers, and before the body limit filter.
 */
@Component
@Order(-1)
public class OwnerIntakeEnabledFilter extends OncePerRequestFilter {

    private static final PathPattern INTAKE_PATH = PathPatternParser.defaultInstance.parse(OwnerIntakeController.PATH);

    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public OwnerIntakeEnabledFilter(ObjectMapper objectMapper,
                                    @Value("${app.acquisition.owner-intake.enabled:false}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /**
     * Matched the way Spring MVC and Spring Security match it: path segments decoded as UTF-8 and matrix parameters
     * dropped. The request's character encoding comes from the client and is not used, so no charset can make the
     * filter see another path than the controller does.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return enabled || !INTAKE_PATH.matches(
                RequestPath.parse(request.getRequestURI(), request.getContextPath()).pathWithinApplication());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.NOT_FOUND,
                AcquisitionExceptionHandler.NOT_FOUND_MESSAGE, List.of())));
    }
}
