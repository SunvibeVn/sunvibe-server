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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Slf4j
@Component
public class JWTRequestFilter extends OncePerRequestFilter {

    private final JWTProvider jwtProvider;
    private final UserDetailsService userDetailsService;

    // Use AntPathMatcher to support wildcard pattern
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // Cache to avoid re-check
    private final Map<String, Boolean> pathCache = new ConcurrentHashMap<>();

    public static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/login", "/api/auth/logout", "/api/users/command/register", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String requestPath = request.getServletPath();

        // Cache hit - return
        if (pathCache.containsKey(requestPath)) {
            return pathCache.get(requestPath);
        }

        // Cache miss - check one time
        boolean isPublic = isPublicPath(requestPath);

        // Save to cache
        pathCache.put(requestPath, isPublic);

        log.info("Path: {} | shouldNotFilter: {}", requestPath, isPublic);

        if (isPublic) {
            log.debug("Public path detected (cached): {}", requestPath);
        }

        return isPublic;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        log.info("ServletPath={}, RequestURI={}", request.getServletPath(), request.getRequestURI());

        // Skip this step if the SecurityContext already has authentication
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            log.info("JWTRequestFilter.doFilterInternal");
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

        log.info("JWTRequestFilter.doFilterInternal -- 2");
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

    private Optional<String> getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(JWTProvider.TOKEN_PREFIX)) {
            return Optional.of(bearerToken.substring(7));
        }
        return Optional.empty();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

}
