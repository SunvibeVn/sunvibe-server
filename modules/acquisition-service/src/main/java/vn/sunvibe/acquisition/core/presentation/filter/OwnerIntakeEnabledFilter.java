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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
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
 * real owners until the staff queue UI exists and the consent text has passed legal review (brief Q-11). While
 * {@code app.acquisition.owner-intake.enabled} is false (the default) every request to the form path is answered 404
 * before the body is read, so no personal data is accepted; the ward catalog stays open. The page flag of the web
 * app only hides the page.
 * <p>
 * Runs after the correlation id filter, the rate limiter and Spring Security, so the 404 carries the correlation id
 * and the CORS headers, and before the body limit filter.
 */
@Component
@Order(-1)
public class OwnerIntakeEnabledFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public OwnerIntakeEnabledFilter(ObjectMapper objectMapper,
                                    @Value("${app.acquisition.owner-intake.enabled:false}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    /** Decoded, normalised path, the way Spring MVC routes it (like the rate limiter). */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return enabled
                || !OwnerIntakeController.PATH.equals(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
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
