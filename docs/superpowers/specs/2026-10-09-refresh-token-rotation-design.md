# Refresh-token flow: rotation, reuse detection, session lifecycle

Date: 2026-10-09 · Module: `modules/auth-service` · Status: approved by the user up front
("implement without asking me to approve any change").

## Goal

Let a signed-in user stay signed in smoothly (no surprise logouts while active, no
logouts from two tabs refreshing at once) while making a stolen refresh token
short-lived and detectable — the behaviour of mainstream apps (Auth0/Okta-style
rotation with a reuse grace period, Google-style "sign out of all devices").

## What existed before

- Login issued a short random refresh token, stored its SHA-256 (as `@Lob`, table
  `REFRESH_TOKEN`, created only by Hibernate `ddl-auto`, no Flyway migration).
- **No refresh endpoint**: the token could never be redeemed. No rotation code
  (only a commented-out sketch), no reuse detection, no expiry clean-up.
- The refresh cookie had path `/api/loginResponse`, which matches no endpoint.
- The access-token cookie (path `/`) was set but never read: the JWT filter only
  read the `Authorization` header, and the login body did not contain the token.
- Logout was Spring's `LogoutFilter` with a handler that wrote a body and then
  redirected to `/login`: nothing was revoked and cookies were not cleared.
- A "fingerprint" (User-Agent + IP) was hashed and stored but never checked.
- Password change/reset did not end other sessions. Reset returned a refresh
  token in the JSON body (with an empty fingerprint).
- Access tokens lived 180 minutes.

## Design

### Tokens
| | Access token | Refresh token |
|---|---|---|
| Form | JWT (HS256), claims `sub`, `uid`, `rol` | opaque, 256-bit `SecureRandom`, base64url |
| Lifetime | 15 min (`app.jwt.expiration.minutes`) | idle 7 days, sliding (`app.jwt.jwtRefreshExpirationMs`); absolute 30 days per session (`app.jwt.refreshAbsoluteExpirationMs`) |
| Transport | JSON body (`accessToken`, `expiresIn`) **and** HttpOnly cookie, path `/` | HttpOnly, Secure, SameSite=Strict cookie only, path `/api/auth` |
| Server state | none (filter reloads the user per request, unchanged) | row per token; only the SHA-256 hex is stored |

The JWT filter reads the `Authorization: Bearer` header first and falls back to the
access-token cookie, so both SPA (memory/header) and cookie-only clients work.

### Session = token family
Login (and register auto-login, password-change re-login) starts a new *family*
(`family_id`). Every refresh **rotates**: the presented token becomes `USED` and a
new `ACTIVE` token in the same family is issued. Its expiry is
`min(now + idleTtl, sessionExpiresAt)`, so an active user is never logged out
before the absolute cap and an idle one is logged out after 7 days.

### Refresh (`POST /api/auth/refresh-token`, public)
Reads the refresh cookie. The token row is loaded with a pessimistic write lock so
concurrent refreshes of the same token are serialised. Outcomes:

1. Missing / unknown / revoked / expired (idle or absolute) → 401.
2. `USED` and used less than `app.jwt.refreshReuseGraceSeconds` (30 s) ago → treated
   as a benign race (two tabs, retried request whose response was lost): a new
   sibling token is issued, nothing is revoked.
3. `USED` outside the grace window → **reuse detected**: the whole family is
   revoked (`REUSE_DETECTED`), a security warning is logged, 401. The revocation
   commits even though the request fails.
4. Owner missing, soft-deleted, locked or disabled → family revoked
   (`ACCOUNT_INACTIVE`), 401.
5. Otherwise rotate, issue a new access token, update the user's roles in the body.

Every 401 from this endpoint also clears both cookies, so the client lands cleanly
on the login screen. Success returns the same body shape as login.

### Logout
- `POST /api/auth/logout` (public, idempotent): revokes the family of the refresh
  cookie if any (`LOGOUT`), always clears both cookies, returns 200 JSON. Spring's
  `LogoutFilter` is disabled; `CustomLogoutSuccessHandler` is deleted.
- `POST /api/auth/logout-all` (authenticated): revokes every session of the user
  (`LOGOUT_ALL`) and clears cookies.

### Password change / reset
- Change: all of the user's sessions are revoked (`PASSWORD_CHANGED`), then the
  controller's existing re-login starts a fresh session for the current device.
- Reset (OTP): all sessions revoked (`PASSWORD_RESET`). The response no longer
  carries `accessToken`/`refreshToken`; the client signs in with the new password.

Already-issued access tokens stay valid until they expire (≤ 15 min). This is the
accepted trade-off of stateless access tokens.

### Housekeeping
A daily job (`app.jwt.refreshCleanupCron`, default 03:30) deletes tokens whose
expiry passed more than a day ago.

### Persistence
New Flyway migration `refresh_tokens`: `id BIGINT` (snowflake), `user_id` FK →
`users(id)` `ON DELETE CASCADE`, `family_id UUID`, `token_hash VARCHAR(64)` unique,
`status`, `issued_at`, `expires_at`, `session_expires_at`, `used_at`, `revoked_at`,
`revoke_reason`, `user_agent`, `ip_address`; indexes on `family_id`, `user_id`,
`expires_at`. User-Agent and IP are kept for audit and a future "active sessions"
screen; they are not enforced (IPs change on mobile networks, which would log
users out for no reason). The old Hibernate-created `refresh_token` table is
unused and can be dropped.

### Layering
- Domain: `RefreshToken` gets `issue`/`load` factories and the rules
  (`isExpired`, `isWithinReuseGrace`, `markUsed`, `rotate`). `RevokeReason` enum.
  `RefreshTokenDomainRepository` speaks domain types (`UserId`, `UUID`, hash).
- Application: `RefreshTokenGenerateUseCase` (start a session),
  `TokenRefreshUseCase`, `LogoutUseCase`, `ExpiredRefreshTokenPurgeUseCase`,
  `InvalidRefreshTokenException`, `ClientInfo` (User-Agent + IP),
  `AuthResultAssembler` (shared by login and refresh).
- Presentation: controller endpoints, `AuthExceptionHandler` (401 + cookie
  clearing), scheduler.

## Out of scope / follow-ups
- Login with a wrong password still returns 500 (the common handler catches
  `jakarta.mail.AuthenticationFailedException` instead of the auth one).
- Instant access-token revocation (session-id claim checked per request).
- "Active sessions" listing / revoke-one-device endpoint.
- `WWW-Authenticate: Bearer error="invalid_token"` on expired access tokens.

## Testing
Domain unit tests for `RefreshToken`; service tests with hand-written fakes for
refresh (rotation, grace, reuse, expiry, absolute cap, inactive user), logout,
password change/reset revocation; `@DataJpaTest` for the adapter (hash lookup,
family/user revocation, purge); JWT filter cookie fallback.
