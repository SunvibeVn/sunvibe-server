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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.sunvibe.auth.core.infrastructure.security.provider.CustomUserDetailsService;
import vn.sunvibe.auth.core.infrastructure.security.provider.JWTProvider;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.AuthenticatedUserPrincipal;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Authenticates a request from its access token, read only from the
 * {@code Authorization: Bearer} header (auth-browser-session BR-5: the API neither sets nor reads an
 * access-token cookie). A missing or invalid token simply leaves the
 * request anonymous; Spring Security then decides whether the endpoint allows it.
 * Public paths ({@link #PUBLIC_PATHS}) are skipped.
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class JWTRequestFilter extends OncePerRequestFilter {

    private final JWTProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * Paths the filter skips: they never need an access token. Nothing under {@code /api/v1/auth/**} takes one
     * (auth-browser-session spec §3.9).
     */
    public static final Set<String> PUBLIC_PATHS = Set.of("/api/v1/auth/**", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");

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
     * used depends on the account as it is now. Reloads the account the {@code uid}
     * claim names and authenticates only if it still exists, the subject is that
     * account (its id, or on a token of the previous release its phone), the token
     * has no older credential version, and the account is neither disabled nor
     * locked. Authorities come from the reloaded user, never from the token. Any
     * failure leaves the request unauthenticated. No line names the subject: on an
     * older token it is a phone number.
     */
    Optional<UsernamePasswordAuthenticationToken> resolveAuthentication(Claims claims) {
        String subject = claims.getSubject();
        String tokenUserId = claims.get(JWTProvider.USER_ID_CLAIM, String.class);
        if (subject == null || tokenUserId == null) {
            log.info("Rejected token without subject or {} claim", JWTProvider.USER_ID_CLAIM);
            return Optional.empty();
        }

        CustomUserDetails user;
        try {
            user = userDetailsService.loadUserById(tokenUserId);
        } catch (UsernameNotFoundException e) {
            log.info("Rejected token: user no longer exists");
            return Optional.empty();
        }

        if (!subject.equals(user.getUserId()) && !subject.equals(user.getPhone())) {
            log.info("Rejected token of user {}: issued to a different account", user.getUserId());
            return Optional.empty();
        }
        if (isOlderCredential(claims, user)) {
            log.info("Rejected token of user {}: older credential version", user.getUserId());
            return Optional.empty();
        }
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            log.info("Rejected token of user {}: account is locked or disabled", user.getUserId());
            return Optional.empty();
        }

        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(user.getUserId());
        return Optional.of(new UsernamePasswordAuthenticationToken(principal, null, user.getAuthorities()));
    }

    /** A token without the claim was issued before it existed and passes until it expires (auth-email-access A-4). */
    private static boolean isOlderCredential(Claims claims, CustomUserDetails user) {
        return claims.get(JWTProvider.CREDENTIAL_VERSION_CLAIM) instanceof Number version
                && version.longValue() < user.credentialVersion();
    }

    /** The access token from {@code Authorization: Bearer ...}; a cookie never carries it. */
    Optional<String> getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(JWTProvider.TOKEN_PREFIX)) {
            return Optional.of(bearerToken.substring(JWTProvider.TOKEN_PREFIX.length()));
        }
        return Optional.empty();
    }

}
