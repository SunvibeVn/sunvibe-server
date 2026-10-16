# shared-service

Shared kernel used by `app` and every domain module. Gradle path `:modules:shared-service`,
Java package `vn.sunvibe.shared` (name set by ADR-004).
No business entities and no repositories here.

## Packages (`vn.sunvibe.shared.*`)

| Package | Contents |
|---|---|
| `exception` | Base exceptions: `DomainException` (+ `BusinessRuleViolationException`, `ResourceNotFoundException`, `ValidationException`), `ApplicationException`, `BaseBusinessException`. Global HTTP mapping for `ApplicationException`, `ValidationException` and `BaseBusinessException` is in `vn.sunvibe.config.GlobalServerExceptionHandler` (`app`); module `*ExceptionHandler`s map the rest |
| `dto.response.error` | Shared error format: `ErrorResponse`, `BaseErrorModel`, `ValidationError` |
| `response` | `ApiResponse` (success envelope of the auth endpoints) |
| `constant`, `constant.error` | `Status`, `SystemErrorMessage` string constants |
| `crypto` | `CryptoService` (field encryption + blind index), `AesGcmCryptoService` (AES-256-GCM, HMAC-SHA256) |
| `generator.id` | `IdGenerator` with `SnowflakeIdGenerator`, `UlidGenerator`, `UuidGenerator` (`@Qualifier` `Snowflake`, `Ulid`, `Uuid`) |
| `security` | `AuthenticatedUser` (caller's user id for any module) |
| `time` | `AppClock` (`currentUTC()` Instant; `currentLocalTime()` LocalDateTime in the JVM default zone) |

Tests: `src/test/java/vn/sunvibe/shared/crypto/AesGcmCryptoServiceTest`.
