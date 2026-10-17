---
paths:
  - "app/src/main/java/**/config/**"
  - "modules/*/src/main/java/**/security/**"
  - "modules/*/src/main/java/**/auth/**"
  - "app/src/main/resources/application*.yml"
---
# Security rules
- One SecurityFilterChain for the whole app (app/.../config/SecurityConfig.java). Stateless.
- Target (ADR-001): access token short-lived, sent as Authorization: Bearer, kept in browser memory.
  The access-token cookie `cookie-jwt` is set only by the legacy paths (/api/auth login and refresh, and
  POST /api/users/command/register), and JWTRequestFilter still accepts it as a fallback on every path it does
  not skip until it is removed. It is legacy: never set it from a new path or extend it.
- Refresh token only in the cookie `sv_refresh` (`__Secure-sv_refresh` in prod, app.auth.refresh-cookie.name):
  Path=/api/v1/auth/session, host-only (no Domain), HttpOnly, SameSite=Strict, Secure (off only in the `lan`
  profile). Never in a v1 response body. The legacy `cookie-jwt-rf` (Path=/api/auth) goes with the legacy paths.
- Refresh endpoint: POST only; rotate refresh tokens on use (already implemented; keep reuse detection).
- CORS allow-list from app.cors.allowed-origins per profile; allowCredentials=true; never "*".
- Authorization checks ownership (a landlord edits only their own listings), not only roles.
- No PII (phone, ID card number, email, address) in logs. ID card numbers stay encrypted (CryptoService).
- Secrets come from env vars only (${...} placeholders). Never add a literal secret to application*.yml.
- Keep rate-limit rules (app.security.rate-limit.rules) in sync when adding public auth endpoints.
