package vn.sunvibe.auth.core.infrastructure.security.provider;


import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
@Slf4j
public class JWTProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecretKey;

    @Value("${app.jwt.expiration.minutes}")
    private Long jwtExpirationMinutes;

    @Value("${app.jwt.jwtCookieName}")
    private String jwtCookie;

    public static final String TOKEN_TYPE = "JWT";
    public static final String TOKEN_ISSUER = "Nestify";
    public static final String TOKEN_PREFIX = "Bearer ";
    /** Claim holding the account's immutable id; the request filter checks it against the reloaded user. */
    public static final String USER_ID_CLAIM = "uid";

    private final UserDetailsService userDetailsService;

    public String generateAccessToken(String username) {

        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(username);

        // Single choke point: no token for a locked/disabled account, whatever
        // the route (login, register auto-login, password reset, refresh).
        if (!userDetails.isAccountNonLocked()) {
            throw new LockedException("Account is locked.");
        }
        if (!userDetails.isEnabled()) {
            throw new DisabledException("Account is disabled.");
        }

        List<String> roles = userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Instant now = Instant.now();
        Instant expiration = now.plus(Duration.ofMinutes(jwtExpirationMinutes));

        return Jwts.builder()
                .header().add("typ", TOKEN_TYPE)
                .and()
                .subject(userDetails.getUsername())
                .claim(USER_ID_CLAIM, userDetails.getUserId())
                .claim("email", userDetails.getEmail())
                .claim("rol", roles)
                .issuer(TOKEN_ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .audience().and()
                .signWith(getSignInKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        SecretKey secret = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecretKey));
        return Jwts.parser().verifyWith(secret).build().parseSignedClaims(token).getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Verifies signature and expiry. Never logs the token itself. Expiry is the
     * normal end of an access token's life (the client then refreshes), so it is
     * logged at debug; anything else hints at a broken or forged token.
     */
    public Optional<Jws<Claims>> validateTokenAndGetJws(String token) {
        try {
            SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecretKey));
            return Optional.of(Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token));
        } catch (ExpiredJwtException exception) {
            log.debug("Rejected expired access token");
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Rejected invalid access token: {}", exception.getMessage());
        }
        return Optional.empty();
    }

    /** The access token from its HttpOnly cookie, or null. */
    public String getJwtFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, jwtCookie);
        return cookie != null ? cookie.getValue() : null;
    }

    private SecretKey getSignInKey() {
        byte[] bytes = Base64.getDecoder()
                .decode(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

}
