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
  Today the API also sets/accepts an access-token cookie; do not extend that, it is a known gap.
- Refresh token only in an HttpOnly, Secure, SameSite=Strict cookie scoped to the auth path
  (today Path=/api/auth, see auth-service CookieService). Never in a response body.
- Refresh endpoint: POST only; rotate refresh tokens on use (already implemented; keep reuse detection).
- CORS allow-list from app.cors.allowed-origins per profile; allowCredentials=true; never "*".
- Authorization checks ownership (a landlord edits only their own listings), not only roles.
- No PII (phone, ID card number, email, address) in logs. ID card numbers stay encrypted (CryptoService).
- Secrets come from env vars only (${...} placeholders). Never add a literal secret to application*.yml.
- Keep rate-limit rules (app.security.rate-limit.rules) in sync when adding public auth endpoints.
