package vn.sunvibe.property.core.presentation.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
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
import vn.sunvibe.shared.constant.error.ErrorCode;
import vn.sunvibe.shared.dto.response.error.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The switches of the inventory intake (property-create intake A-11, D5-17). While the intake is off every path of it answers
 * 404, and while direct create is off so does POST /api/v1/properties. It runs after Spring Security, as
 * OwnerIntakeEnabledFilter does, so a call with no token still gets its 401 and any token gets the 404, and the answer
 * carries the correlation id and the CORS headers. Paths match as MVC and Security match them.
 */
@Component
@Order(-1)
public class IntakeEnabledFilter extends OncePerRequestFilter {

    private static final String NOT_FOUND_MESSAGE = "The resource was not found.";
    private static final List<PathPattern> INTAKE_PATHS = List.of(
            PathPatternParser.defaultInstance.parse("/api/v1/staff/owner-leads/*/inventory-intake/**"),
            PathPatternParser.defaultInstance.parse("/api/v1/staff/inventory-cases/**"),
            PathPatternParser.defaultInstance.parse("/api/v1/me/property-confirmations/**"));
    private static final PathPattern DIRECT_CREATE_PATH = PathPatternParser.defaultInstance.parse("/api/v1/properties");

    private final ObjectMapper objectMapper;
    private final boolean intakeEnabled;
    private final boolean directCreateEnabled;

    public IntakeEnabledFilter(ObjectMapper objectMapper,
                               @Value("${app.property.intake.enabled:false}") boolean intakeEnabled,
                               @Value("${app.property.direct-create.enabled:true}") boolean directCreateEnabled) {
        this.objectMapper = objectMapper;
        this.intakeEnabled = intakeEnabled;
        this.directCreateEnabled = directCreateEnabled;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        PathContainer path = RequestPath.parse(request.getRequestURI(), request.getContextPath()).pathWithinApplication();
        boolean closedIntake = !intakeEnabled && INTAKE_PATHS.stream().anyMatch(pattern -> pattern.matches(path));
        boolean closedCreate = !directCreateEnabled && HttpMethod.POST.matches(request.getMethod())
                && DIRECT_CREATE_PATH.matches(path);
        return !closedIntake && !closedCreate;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE,
                List.of())));
    }
}
