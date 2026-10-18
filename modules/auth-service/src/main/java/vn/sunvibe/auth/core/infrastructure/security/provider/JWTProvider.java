package vn.sunvibe.auth.core.infrastructure.security.provider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import vn.sunvibe.auth.core.application.usecase.external.token.AccessTokenIssuerPort;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Issues and verifies the HMAC-SHA256 signed access tokens (JWT).
 * <p>
 * Claims: {@code sub} = username, {@link #USER_ID_CLAIM} = account id,
 * {@code email}, {@code rol} = authorities at issue time (informational only:
 * the request filter reloads authorities from the database).
 */
@Component
@Slf4j
public class JWTProvider implements AccessTokenIssuerPort {

    public static final String TOKEN_TYPE = "JWT";
    public static final String TOKEN_ISSUER = "Nestify";
    public static final String TOKEN_PREFIX = "Bearer ";
    /** Claim holding the account's immutable id; the request filter checks it against the reloaded user. */
    public static final String USER_ID_CLAIM = "uid";

    private final SecretKey signingKey;
    private final Duration lifetime;

    /**
     * @param jwtSecretKey          base64-encoded HMAC key, at least 256 bits
     * @param jwtExpirationMinutes  lifetime of an access token
     */
    public JWTProvider(@Value("${app.jwt.secret}") String jwtSecretKey,
                       @Value("${app.jwt.expiration.minutes}") long jwtExpirationMinutes) {
        // Always HS256, whatever the key length
        this.signingKey = new SecretKeySpec(Decoders.BASE64.decode(jwtSecretKey), "HmacSHA256");
        this.lifetime = Duration.ofMinutes(jwtExpirationMinutes);
    }

    /**
     * Single choke point: no token for a locked or disabled account, whatever
     * the route (login, registration, password change, refresh).
     *
     * @throws LockedException   if the account is locked by an administrator
     * @throws DisabledException if the account is terminated or deleted
     */
    @Override
    public String issue(AppUser user) {
        if (user.getUserStatus() == UserStatus.LOCK) {
            throw new LockedException("Account is locked.");
        }
        if (!user.canAuthenticate()) {
            throw new DisabledException("Account is disabled.");
        }

        List<String> roles = CustomUserDetailsService.authoritiesOf(user).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Instant now = Instant.now();
        return Jwts.builder()
                .header().add("typ", TOKEN_TYPE)
                .and()
                .subject(user.getUsername().getValue())
                .claim(USER_ID_CLAIM, user.getId().getValue())
                .claim("email", user.getEmail() != null ? user.getEmail().getValue() : null)
                .claim("rol", roles)
                .issuer(TOKEN_ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .audience().and()
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature and expiry. Never logs the token itself. Expiry is the
     * normal end of an access token's life (the client then refreshes), so it is
     * logged at debug; anything else hints at a broken or forged token.
     */
    public Optional<Jws<Claims>> validateTokenAndGetJws(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token));
        } catch (ExpiredJwtException exception) {
            log.debug("Rejected expired access token");
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Rejected invalid access token: {}", exception.getMessage());
        }
        return Optional.empty();
    }

}
