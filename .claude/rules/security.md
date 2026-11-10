---
paths:
  - "app/src/main/java/**/config/**"
  - "modules/*/src/main/java/**/security/**"
  - "modules/*/src/main/java/**/auth/**"
  - "modules/*/src/main/java/**/presentation/**"
  - "modules/*/src/main/java/**/core/application/**"
  - "modules/*/src/main/java/**/core/infrastructure/**"
  - "app/src/main/resources/application*.yml"
---
# Security rules
- One SecurityFilterChain for the whole app (app/.../config/SecurityConfig.java). Stateless; CSRF is off because no
  cookie authenticates an API call except the refresh cookie below (SameSite=Strict, path-scoped, Origin-checked).
  DispatcherType.ERROR is permitted on purpose: it renders a request the chain already decided (SV-069).
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
  listings, a SALE sees only own leads), not only roles. An API test proves the IDOR case. The chain ends in
  anyRequest().authenticated(), so an endpoint with no rule of its own is open to every signed-in user.
- Take the actor from the security principal (AuthenticatedUser), never from the request body, query or path.
- Field level: a response DTO carries only what the caller's role may see; a request DTO has no field the caller may
  not set (mass assignment).
- A public request does not reveal through its response, log or error whether an account exists, beyond what the
  security spec approved (auth-email-access BR-7).
- Input: every request DTO is a @Valid record with bounds (@NotBlank, @Size, @Min/@Max, @Pattern); SQL only through
  JPA or bound parameters, never string concatenation; never return exception messages, stack traces or request values
  in an error. Free text that reaches PostgreSQL refuses NUL (U+0000): the database cannot store it, and it may answer
  500 (SV-087, not reproduced). Keep spring.jackson.datatype.enum.fail-on-numbers-for-enums on: without it a JSON number
  selects an enum by ordinal.
- CORS allow-list from app.cors.allowed-origins per profile; allowCredentials=true; never "*".
- No PII (phone, ID card number, email, address) in logs. The username, when an account has one, is a phone number: log
  the user id or a fixed reason code (SV-009). Bound any client-controlled value in a log line and strip CR/LF (log injection). ID card numbers
  stay encrypted (CryptoService). Keep logServerErrorDetail=false: PostgreSQL error text would log row values.
- Secrets come from env vars only (${...} placeholders). Never add a literal secret to application*.yml.
- OTPs compared in Java use MessageDigest.isEqual; passwords go through PasswordEncoder. A new high-entropy token is
  stored hashed and found by its hash, as refresh, registration and password reset tokens are. A stored one-time code
  goes through OtpHasher (keyed HMAC, AUTH_OTP_HASH_KEY): a plain hash of six digits falls to a reader of the table.
  Randomness comes from SecureRandom or UUID.randomUUID, never Random.
- Keep rate-limit rules (app.security.rate-limit.rules) in sync when adding public endpoints. The limiter is in memory,
  per instance, and keys on getRemoteAddr(): never read X-Forwarded-For yourself, any client can spoof it.
  server.forward-headers-strategy is not set in any profile: behind a proxy, getRemoteAddr() is the proxy (SV-016, SV-017).
  The one-VM test environment sets it in its env file (scripts/ops/env/test-env.env.example): `native`, with
  server.tomcat.remoteip.internal-proxies matching only Caddy's fixed address, and Caddy replacing or dropping the
  client's forwarding headers; scripts/ops/test-env/smoke.sh checks (c) and (d) prove it on a running stack. Never
  `framework`, which trusts every client's headers. The start-up guard against a wrong setting is still pending (SV-016).
- A new path filter matches like MVC and Security (PathPattern on RequestPath): UrlPathHelper decodes with the
  request's charset, which depends on Boot forcing UTF-8 (SV-019; not exploitable with the current config). Check a new
  filter's order against CORS, rate limit and JWT.
- Uploads follow ADR-011: type and size checked on the server, no path from user input, no public listing.
- The server never fetches a URL taken from a request (SSRF). A new outbound call has a fixed or allow-listed host and
  a timeout, and its response is untrusted input.
- No actuator dependency is on the classpath: SecurityConfig permits /actuator/health, but the path does not exist and
  the `management:` block in application.yml is inert. Adding the starter as configured would show health details to
  anonymous callers (`show-details: always`), so it is a security change; so is any new public docs path
  (application-prod.yml turns the API docs off).
- Some items adapted from ECC skills/springboot-security (commit 76aaa77, MIT, Copyright (c) 2026 Affaan Mustafa);
  its Supabase, session-cookie and BFF examples do not apply. See ../docs/setup/AI_FLOW.md section 12.

References: [OWASP API Security Top 10 2023](https://api-security.owasp.org/editions/2023/en/0x11-t10) (API1, API3, API4,
API5, API7, API8, API10); OWASP cheat sheets [REST](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html),
[Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html),
[Mass Assignment](https://cheatsheetseries.owasp.org/cheatsheets/Mass_Assignment_Cheat_Sheet.html),
[Logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html);
Spring Security [authorizing requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html).
