package vn.sunvibe.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.infrastructure.security.filter.JWTRequestFilter;

/**
 * Centralized Security Configuration for the entire application.
 * <p>
 * This configuration is placed in the app module (root module) to ensure
 * there is only one SecurityFilterChain for the entire modular monolith.
 * <p>
 * Security components (JWT filter, handlers) are provided by auth-service module.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final AuthenticationConfiguration authConfiguration;
    private final JWTRequestFilter jwtRequestFilter;

    // ── Public Endpoints ────────────────────────────────────────────
    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/users/command/register",
            // Phone verification before registration: the person has no account (and no token) yet
            "/api/auth/registration/phone/send",
            "/api/auth/registration/phone/verify",
            "/api/auth/forgot-password",
            "/api/auth/forgot-password/sms",
            "/api/auth/reset-password",
            "/api/auth/login",
            // Called when the access token has expired, so it cannot require one
            "/api/auth/refresh-token",
            // Identifies the session by its refresh-token cookie; must work with an expired access token
            "/api/auth/logout",
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**"
    };

    private static final String ADMIN_FEATURE_PATH = "/features/admin/**";

    // ── Beans ────────────────────────────────────────────────

    /**
     * SUPER_ADMIN passes every ADMIN rule (auth-roles BR-4, W6-4). The one hierarchy for both the HTTP rules
     * below (picked up from the context by authorizeHttpRequests) and method security ({@code @PreAuthorize}).
     * Static, like every bean that method security needs early.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(RoleName.SUPER_ADMIN.name()).implies(RoleName.ADMIN.name())
                .build();
    }

    /** Makes {@code @PreAuthorize("hasRole(...)")} use {@link #roleHierarchy()}. */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultAuthorizationManagerFactory<MethodInvocation> authorizationManagerFactory =
                new DefaultAuthorizationManagerFactory<>();
        authorizationManagerFactory.setRoleHierarchy(roleHierarchy);
        DefaultMethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();
        expressionHandler.setAuthorizationManagerFactory(authorizationManagerFactory);
        return expressionHandler;
    }

    @Bean
    public AuthenticationManager authenticationManager() throws Exception {
        return authConfiguration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            log.warn("Unauthorized request to {}: {}", request.getRequestURI(), authException.getMessage());

            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("""
                    {
                      "error": "Unauthorized",
                      "message": "%s",
                      "path": "%s"
                    }
                    """.formatted(authException.getMessage(), request.getRequestURI()));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            log.warn("Access denied to {}: {}", request.getRequestURI(), accessDeniedException.getMessage());

            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("""
                    {
                      "error": "Forbidden",
                      "message": "You do not have permission to access this resource.",
                      "path": "%s"
                    }
                    """.formatted(request.getRequestURI()));
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, ADMIN_FEATURE_PATH).hasRole(RoleName.ADMIN.name())
                        .anyRequest().authenticated()
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)

                // Logout is a regular endpoint (AuthController) that revokes the session's refresh tokens
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
