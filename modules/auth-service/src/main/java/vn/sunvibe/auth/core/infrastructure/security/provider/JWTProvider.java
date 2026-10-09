package vn.sunvibe.auth.core.infrastructure.security.provider;


import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;
import vn.sunvibe.auth.core.domain.exception.auth.TokenExpiredException;
import vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetails;
import vn.sunvibe.auth.core.shared.constant.AuthMessage;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    @Value("${app.jwt.jwtRefreshCookieName}")
    private String jwtRefreshCookie;

    @Value("${app.jwt.expiration.minutes}")
    private Long jwtExpiration;

    @Value("${app.jwt.jwtRefreshExpirationMs}")
    private Long jwtRFTExpiration;

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

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiration = now.plusMinutes(jwtExpirationMinutes);

//        log.info("JWTProvider.generateToken");
        return Jwts.builder()
                .header().add("typ", TOKEN_TYPE)
                .and()
                .subject(userDetails.getUsername())
                .claim(USER_ID_CLAIM, userDetails.getUserId())
                .claim("email", userDetails.getEmail())
                .claim("rol", roles)
                .issuer(TOKEN_ISSUER)
                .issuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(Date.from(expiration.atZone(ZoneId.systemDefault()).toInstant()))
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

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public void validateToken(String token) {
        if (isTokenExpired(token)) {
            throw new TokenExpiredException(AuthMessage.TOKEN_EXPIRED);
        }
    }

    public Optional<Jws<Claims>> validateTokenAndGetJws(String token) {

        try {
            validateToken(token);

            SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecretKey));

            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);

            return Optional.of(jws);
        } catch (ExpiredJwtException exception) {
            log.error("Request to parse expired JWT : {} failed : {}", token, exception.getMessage());
        } catch (UnsupportedJwtException exception) {
            log.error("Request to parse unsupported JWT : {} failed : {}", token, exception.getMessage());
        } catch (MalformedJwtException exception) {
            log.error("Request to parse incorrect format JWT : {} failed : {}", token, exception.getMessage());
        } catch (SecurityException exception) {
            log.error("Request to parse invalid JWT: {} failed : {}", token, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            log.error("Request to parse empty or null JWT : {} failed : {}", token, exception.getMessage());
        } catch (TokenExpiredException exception) {
            log.error("Request to parse expired JWT : {} failed : {}", token, exception.getMessage());
        }

        return Optional.empty();
    }

    public String getJwtFromCookies(HttpServletRequest request) {
        return getCookieValueByName(request, jwtCookie);
    }

    public String getJwtRefreshFromCookies(HttpServletRequest request) {
        return getCookieValueByName(request, jwtRefreshCookie);
    }

    public ResponseCookie getCleanJwtCookie() {
        return ResponseCookie.from(jwtCookie, null).path("/api").build();
    }

    public ResponseCookie getCleanJwtRefreshCookie() {
        return ResponseCookie.from(jwtRefreshCookie, null).path("/api/loginResponse/refreshtoken").build();
    }

    private String getCookieValueByName(HttpServletRequest request, String name) {
        Cookie cookie = WebUtils.getCookie(request, name);
        if (cookie != null) {
            return cookie.getValue();
        } else {
            return null;
        }
    }

    private SecretKey getSignInKey() {
        byte[] bytes = Base64.getDecoder()
                .decode(jwtSecretKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    public ResponseCookie generateJwtCookie(String jwt) {
        return generateCookie(jwtCookie, jwt, "/api/loginResponse/access-token", jwtExpiration);
    }

    public ResponseCookie generateRefreshJwtCookie(String refreshTokenPlainText) {
        return generateCookie(jwtRefreshCookie, refreshTokenPlainText, "/api/loginResponse/refresh-token", jwtRFTExpiration);
    }

    private ResponseCookie generateCookie(String name, String value, String path, Long maxAge) {
        return ResponseCookie.from(name, value)
                .path(path)
                .maxAge(maxAge)
                .httpOnly(true)
                .secure(true)
                .build();
    }

}
