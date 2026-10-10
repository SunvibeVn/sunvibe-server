package vn.sunvibe.auth.core.infrastructure.security.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Authenticates a request from its access token ({@code Authorization: Bearer}
 * header or HttpOnly cookie). A missing or invalid token simply leaves the
 * request anonymous; Spring Security then decides whether the endpoint allows it.
 * Public paths ({@link #PUBLIC_PATHS}) are skipped.
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class JWTRequestFilter extends OncePerRequestFilter {

    private final JWTProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** Paths the filter skips: they never need an access token. */
    public static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/login", "/api/auth/logout", "/api/auth/refresh-token", "/api/users/command/register", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // No cache: matching a handful of patterns is cheap, and a cache keyed by
        // client-chosen paths would grow without bound
        String requestPath = request.getServletPath();
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Skip this step if the SecurityContext already has authentication
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            getJwtFromRequest(request)
                    .flatMap(jwtProvider::validateTokenAndGetJws)
                    .flatMap(jws -> resolveAuthentication(jws.getPayload()))
                    .ifPresent(authentication -> {
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Authenticated user: {}", authentication.getPrincipal());
                    });
        } catch (Exception e) {
            log.error("Cannot set user authentication", e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * A valid signature only proves the token was issued; whether it may still be
     * used depends on the account as it is now. Reloads the user and authenticates
     * only if it still exists, is the same account the token was issued for (the
     * {@code uid} claim matches, so a re-registered username can't inherit an old
     * token) and is neither disabled nor locked. Authorities come from the reloaded
     * user, never from the token. Any failure leaves the request unauthenticated.
     */
    Optional<UsernamePasswordAuthenticationToken> resolveAuthentication(Claims claims) {
        String username = claims.getSubject();
        String tokenUserId = claims.get(JWTProvider.USER_ID_CLAIM, String.class);
        if (username == null || tokenUserId == null) {
            log.info("Rejected token without subject or {} claim", JWTProvider.USER_ID_CLAIM);
            return Optional.empty();
        }

        UserDetails loaded;
        try {
            loaded = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            log.info("Rejected token for {}: user no longer exists", username);
            return Optional.empty();
        }

        if (!(loaded instanceof CustomUserDetails user) || !tokenUserId.equals(user.getUserId())) {
            log.info("Rejected token for {}: issued to a different account", username);
            return Optional.empty();
        }
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            log.info("Rejected token for {}: account is locked or disabled", username);
            return Optional.empty();
        }

        return Optional.of(new UsernamePasswordAuthenticationToken(user.getUsername(), null, user.getAuthorities()));
    }

    /**
     * The access token from {@code Authorization: Bearer ...} (SPA/mobile), or
     * else from the HttpOnly access-token cookie set at login/refresh (browser).
     */
    Optional<String> getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(JWTProvider.TOKEN_PREFIX)) {
            return Optional.of(bearerToken.substring(JWTProvider.TOKEN_PREFIX.length()));
        }
        String cookieToken = jwtProvider.getJwtFromCookies(request);
        return StringUtils.hasText(cookieToken) ? Optional.of(cookieToken) : Optional.empty();
    }

}
