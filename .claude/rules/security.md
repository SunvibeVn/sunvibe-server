---
paths:
  - "app/src/main/java/**/config/**"
  - "modules/*/src/main/java/**/security/**"
  - "modules/*/src/main/java/**/auth/**"
  - "app/src/main/resources/application*.yml"
---
# Security rules
- One SecurityFilterChain for the whole app (app/.../config/SecurityConfig.java). Stateless; CSRF is off because no
  cookie authenticates an API call except the refresh cookie below (SameSite=Strict, path-scoped, Origin-checked).
- Access token (ADR-001): short-lived JWT, read only from `Authorization: Bearer` (JWTRequestFilter); the API sets
  and reads no access-token cookie. Paths that never take a token: JWTRequestFilter.PUBLIC_PATHS and the public
  lists of SecurityConfig.
- Refresh token only in the cookie `sv_refresh` (`__Secure-sv_refresh` in prod, app.auth.refresh-cookie.name):
  Path=/api/v1/auth/session, host-only (no Domain), HttpOnly, SameSite=Strict, Secure (off only in the `lan`
  profile). Never in a response body. Refresh and logout also need an allowed Origin (RefreshOriginGuard).
- Refresh endpoint: POST /api/v1/auth/session/refresh only; rotate refresh tokens on use (keep reuse detection).
- The legacy auth paths (/api/auth, /api/users/command, /api/user/query) and the cookie-jwt / cookie-jwt-rf cookies
  were removed (LegacyAuthPathsRemovedApiTest); never re-add them.
- Authorization: deny by default; every endpoint has an explicit rule (SecurityConfig path rule or @PreAuthorize,
  which uses the role hierarchy), and the use case checks ownership or scope (a landlord edits only their own
  listings, a SALE sees only own leads), not only roles. An API test proves the IDOR case.
- Input: every request DTO is a @Valid record with bounds (@NotBlank, @Size, @Min/@Max, @Pattern); SQL only through
  JPA or bound parameters, never string concatenation; never return exception messages or stack traces.
- CORS allow-list from app.cors.allowed-origins per profile; allowCredentials=true; never "*".
- No PII (phone, ID card number, email, address) in logs. ID card numbers stay encrypted (CryptoService).
- Secrets come from env vars only (${...} placeholders). Never add a literal secret to application*.yml.
- Keep rate-limit rules (app.security.rate-limit.rules) in sync when adding public endpoints.
- Some items adapted from ECC skills/springboot-security (commit 76aaa77, MIT, Copyright (c) 2026 Affaan Mustafa);
  its Supabase, session-cookie and BFF examples do not apply. See ../docs/setup/AI_FLOW.md section 12.
