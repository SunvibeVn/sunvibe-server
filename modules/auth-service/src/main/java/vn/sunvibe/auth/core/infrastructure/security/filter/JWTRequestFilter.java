package vn.sunvibe.auth.core.infrastructure.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Component
public class JWTRequestFilter extends OncePerRequestFilter {

    private final JWTProvider jwtProvider;

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
            getJwtFromRequest(request).flatMap(jwtProvider::validateTokenAndGetJws).ifPresent(jws -> {
                String username = jws.getPayload().getSubject();
                List<String> roles = jws.getPayload().get("rol", List.class);
                List<GrantedAuthority> authorities = roles == null ? List.of() : roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user: {}", username);
            });
        } catch (Exception e) {
            log.error("Cannot set user authentication", e);
        }

        log.info("JWTRequestFilter.doFilterInternal -- 2");
        filterChain.doFilter(request, response);
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
