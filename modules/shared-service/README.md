# shared-service

Shared kernel used by `app` and every domain module. Gradle path `:modules:shared-service`,
Java package `vn.sunvibe.shared` (name set by ADR-004).
No business entities and no repositories here.

## Packages (`vn.sunvibe.shared.*`)

| Package | Contents |
|---|---|
| `exception` | Base exceptions: `DomainException` (+ `BusinessRuleViolationException`, `ResourceNotFoundException`, `ValidationException`), `ApplicationException`, `InfrastructureException` (+ `SendingMailFailedException`), `BaseBusinessException`; `GlobalServerExceptionHandler` (`@RestControllerAdvice`) |
| `dto.response.error` | Shared error format: `ErrorResponse`, `BaseErrorModel`, `ValidationError` |
| `dto` | `BaseResponse`, `PageResponse` |
| `response` | `ApiResponse` |
| `constant`, `constant.error` | `Status`, `SystemErrorMessage` string constants |
| `crypto` | `CryptoService` (field encryption + blind index), `AesGcmCryptoService` (AES-256-GCM, HMAC-SHA256) |
| `generator.id` | `IdGenerator` with `SnowflakeIdGenerator`, `UlidGenerator`, `UuidGenerator` (`@Qualifier` `Snowflake`, `Ulid`, `Uuid`) |
| `security` | `AuthenticatedUser` (caller's user id for any module), `SecurityRuleContributor` (contract for module authorization rules; no implementation yet) |
| `time` | `AppClock` (`currentUTC()` Instant; `currentLocalTime()` LocalDateTime in the JVM default zone) |
| `utils.date`, `utils.http` | `DateUtils`, `HttpRequestUtils` (client IP from X-Forwarded-For / remote address; `User-Agent` header name) |
| `config` | `OpenApiConfig`, `RedisConfig`, `AuditConfig`: empty classes today |

Tests: `src/test/java/vn/sunvibe/shared/crypto/AesGcmCryptoServiceTest`.
