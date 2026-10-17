package vn.sunvibe.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * The two security schemes of the API contract (auth-browser-session spec §3.11). Operations opt in one by one: the
 * {@code /api/v1/me} endpoints declare {@code bearerAuth}, refresh and logout declare {@code refreshCookie}, and there
 * is no global requirement, so the public {@code /api/v1/auth} endpoints declare none.
 */
@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityScheme(name = "refreshCookie", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.COOKIE,
        paramName = "sv_refresh",
        description = "Refresh-token cookie, sent only to /api/v1/auth/session/**; named __Secure-sv_refresh in prod")
public class OpenApiSecurityConfig {
}
