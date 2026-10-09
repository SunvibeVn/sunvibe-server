# Auth user/role schema sync — design spec

Date: 2026-10-09
Status: Approved for planning
Scope module: `modules/auth-service` (plus a small shared addition to `modules/common`)

## 1. Context and intent

`dev_docs/06_DATABASE_SCHEMA_POSTGRESQL.md` contains a hand-written PostgreSQL DDL reference
(`CREATE TABLE users/roles/user_roles ...`) that has drifted from the actual JPA entities in
`modules/auth-service` (`UserEntity`, `UserRoleEntity`) and the `AppUser` aggregate. The doc uses
column names like `phone_number`/`avatar_url` and a DB-generated `UUID` primary key, while the real
code uses `username`/`profile_image` and an application-generated ULID `String` id. The doc also
describes several columns (`id_card_number`, `id_card_front_url`, `id_card_back_url`, `kyc_status`,
`deleted_at`) that don't exist in the Java code at all.

The goal of this change is to make the SQL schema and the Java code consistent with each other —
using the **Java code's existing property names/types as the source of truth for naming**, and the
**SQL doc's columns as the source of truth for which fields must exist** — then introduce real
Flyway migrations for `users`, `roles`, and `user_roles` so schema changes stop depending on
Hibernate's `ddl-auto: update`. Scope is intentionally limited to registration, login, and
authentication — no other business domain (property-service, booking-service, etc.) is touched,
and no KYC review/approval workflow is being built, only the data model needed to support one later.

Flyway is already a declared dependency (`app/build.gradle.kts`) with an empty `db/migration`
placeholder already present in several modules, but no migration files exist anywhere yet and
`ddl-auto: update` is what has actually been shaping the schema so far.

## 2. Decisions made during design (with rationale)

These were confirmed with the project owner before writing this spec:

1. **KYC field scope at registration:** only `idCardNumber` becomes a required registration input.
   `idCardFrontUrl`, `idCardBackUrl`, and `kycStatus` exist in the schema/domain model for
   completeness but are not collected or validated at registration time — there is no file-upload
   flow or KYC review workflow in scope. `kycStatus` defaults to `UNVERIFIED`.
2. **Encryption:** `idCardNumber` is encrypted at rest (the schema doc's comment explicitly says
   "Được mã hóa" / "encrypted"). No crypto utility exists anywhere in the codebase today, so one is
   built as part of this change.
3. **Soft-delete reuse:** a deleted user's `username`/`email` become reusable by a new registration.
   `deletedAt` is added, and all existence/lookup queries exclude soft-deleted rows.
4. **Login status enforcement:** `CustomUserDetails.isEnabled()`/`isAccountNonLocked()` currently
   never override the `UserDetails` defaults, so a `LOCK`/`TERMINATED`/`DELETED` user can still log
   in successfully today. This is fixed as part of this change, since it's core login/authentication
   logic and directly interacts with the new `deletedAt`/status work.

## 3. Domain layer changes (`modules/auth-service` domain)

### New value object: `IdCardNumber`

- Package: `domain/model/user/vo/IdCardNumber.java`.
- Wraps a single `String value`. Validates `^(\d{9}|\d{12})$` (Vietnamese CMND 9-digit or CCCD
  12-digit format).
- Follows the documented VO convention properly: **private constructor**, `create(String)` (new
  data, validates) and `from(String)` (reconstruction, no re-validation) static factories. Unlike
  the existing VOs in this codebase (`Email`, `Username`, etc.), it does not rely on a
  Lombok-generated public constructor — the constructor is hand-written and private.
- Extends the module's existing `ValueObject<T>` base class (`domain/model/shared/ValueObject.java`),
  implementing `equalsCore`/`hashCodeCore`.
- Javadoc on the class and both factory methods explaining the validation rule.

### New enum: `KycStatus`

- Package: `domain/model/user/enums/KycStatus.java`.
- Values: `UNVERIFIED, PENDING, VERIFIED, REJECTED`.

### `AppUser` aggregate changes

New fields: `idCardNumber` (`IdCardNumber`, required), `idCardFrontUrl` / `idCardBackUrl`
(`String`, nullable, unvalidated — just storage), `kycStatus` (`KycStatus`), `deletedAt`
(`Instant`, nullable).

- `create(...)` signature extended to accept `IdCardNumber idCardNumber`; always sets
  `kycStatus = KycStatus.UNVERIFIED`, `idCardFrontUrl = null`, `idCardBackUrl = null`,
  `deletedAt = null` for a brand-new registration.
- `deactivate()` sets **both** `userStatus = UserStatus.DELETED` and `deletedAt = AppClock.currentUTC()`.
- **Bug fix required to support this correctly:** `AppUser.from(...)` today is a near-duplicate of
  `create(...)` that force-sets `UserStatus.ACTIVE` instead of reconstructing persisted state, which
  cannot correctly round-trip `kycStatus`/`deletedAt`/the new fields from the database. Replace it
  with a `load(...)` factory (matching the CLAUDE.md-documented reconstruction-factory convention)
  that takes and faithfully restores every persisted field, including the new ones. Update
  `UserMapper.toDomain(...)` to call `AppUser.load(...)` instead of `AppUser.from(...)`. Remove the
  old `from(...)` method — it becomes dead code once `load(...)` exists.

## 4. Encryption design

### Problem

AES-GCM (the standard choice for encrypting a field at rest) uses a random IV per encryption, so
the same plaintext produces different ciphertext every time. That is correct and desirable for
confidentiality, but it means a SQL `UNIQUE` constraint on the encrypted column — and a
duplicate-registration check, matching how `username`/`email` are already checked — cannot work
directly against the ciphertext.

### Solution: encrypted value + blind index

Two physical columns back the one logical `idCardNumber` domain field:

- `id_card_number` — AES-256-GCM ciphertext (base64: IV + ciphertext + auth tag concatenated).
- `id_card_number_hash` — an HMAC-SHA256 "blind index": `hex(HMAC-SHA256(indexKey, normalizedValue))`.
  Deterministic (same input → same hash), `UNIQUE`, used only to answer "does this ID card number
  already exist?" Plain `SHA-256` (unkeyed) is deliberately avoided — the ID-number keyspace (10^9
  or 10^12 values) is small enough that an unkeyed hash is at least partially precomputable; a keyed
  HMAC is not, as long as the index key stays secret.

This is an infrastructure-only split — the domain `AppUser`/`IdCardNumber` remains a single plain
field. The mapper/service layer computes both the ciphertext and the hash when persisting.

### `CryptoService` (new, `modules/common`)

Package: `vn.sunvibe.common.crypto`. Chosen to live in `common` (not `auth-service`) since
encryption-at-rest is a generic capability other modules may eventually need.

```java
public interface CryptoService {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
    String blindIndex(String value);
}
```

- `AesGcmCryptoService implements CryptoService` — AES/GCM/NoPadding, 256-bit key, random 96-bit IV
  per call, output is base64(IV || ciphertext || tag). Encryption key read from
  `${app.crypto.id-card-key}` (base64-encoded 32-byte key), separate `${app.crypto.id-card-index-key}`
  for the HMAC blind index (different key than the encryption key, standard practice — an index-key
  compromise shouldn't also compromise confidentiality, and vice versa).
- Both keys get a generated-random dev-only default in `application.yml` with an inline comment
  that production **must** override them via environment variables — no real secret is committed.
- Javadoc on the interface and the implementation class explaining the blind-index approach and the
  key-separation rationale.

### JPA wiring

`EncryptedStringAttributeConverter implements AttributeConverter<String, String>`
(`infrastructure/persistence/converter/` in auth-service), `@Converter(autoApply = false)`,
`@Component`, constructor-injects `CryptoService`. Applied to `UserEntity.idCardNumber` via
`@Convert(converter = EncryptedStringAttributeConverter.class)`. Spring Boot's JPA
auto-configuration already lets Hibernate resolve `@Convert` converters as Spring beans, so no
extra wiring is required beyond `@Component` + constructor injection.

The hash column is **not** a JPA converter — it's a plain `String` field
(`UserEntity.idCardNumberHash`) computed explicitly in `UserRepositoryAdapter`/`UserMapper` via
`cryptoService.blindIndex(...)` before save, because it must remain directly queryable
(`existsByIdCardNumberHash`).

## 5. Infrastructure layer changes

### `UserEntity`

New columns/fields:
- `idCardNumber` → `id_card_number` (`TEXT`, not null, `@Convert` as above).
- `idCardNumberHash` → `id_card_number_hash` (`VARCHAR(64)`, not null, unique).
- `idCardFrontUrl` → `id_card_front_url` (`TEXT`, nullable).
- `idCardBackUrl` → `id_card_back_url` (`TEXT`, nullable).
- `kycStatus` → `kyc_status` (`VARCHAR(20)`, not null, `@Enumerated(STRING)`).
- `deletedAt` → `deleted_at` (`TIMESTAMP WITH TIME ZONE`, nullable).

Fixes:
- `username`, `email`, `fullName` get explicit `nullable=false` (currently implicit/inconsistent).
- `id` stays `String` (ULID) — fix `UserJpaRepository extends JpaRepository<UserEntity, UUID>` to
  `JpaRepository<UserEntity, String>` (the generic currently doesn't match the actual `@Id` type).

`UserRoleEntity` (table `ROLES`) needs no structural change — it already matches the target shape
(`UUID id`, unique `name`, `description`). The migration simply codifies what already exists.

### Repository / adapter changes

- `UserJpaRepository.findByUsername` / `findByEmail` — add `AND u.deletedAt IS NULL` to the
  existing `@Query`.
- `existsByUsername`, `existsByEmail` — same `deletedAt IS NULL` filter, so a soft-deleted account's
  username/email become available to a new registration.
- New `existsByIdCardNumberHash(String hash)`, also filtered by `deletedAt IS NULL`.
- `UserDomainRepository` port and `UserRepositoryAdapter` get the matching
  `existsByIdCardNumberHash` method; `save(AppUser)` is extended to populate the new entity fields
  (computing the hash via `CryptoService` at save time).

### Login status enforcement

`CustomUserDetails` gains the `userStatus` field; `CustomUserDetailsService` passes it through.
- `isEnabled()` → `false` when `userStatus` is `DELETED` or `TERMINATED`.
- `isAccountNonLocked()` → `false` when `userStatus == LOCK`.

Spring Security's `AuthenticationManager` already converts these into the standard
`DisabledException`/`LockedException` during authentication — no hand-rolled check needed in
`LoginService`.

## 6. Flyway migrations

New files in `modules/auth-service/src/main/resources/db/migration/` (the empty placeholder
directory that already exists there):

- `V202610091200__create_roles_table.sql`
- `V202610091201__create_users_table.sql`
- `V202610091202__create_user_roles_table.sql`

**Versioning scheme:** timestamp-based (`V{yyyyMMddHHmm}`) rather than sequential (`V1`, `V2`, ...).
Flyway's default `classpath:db/migration` location is shared across every module's JAR once
packaged together, and `property-service`, `booking-service`, and `listing-service` already have
their own empty `db/migration` placeholders. Sequential numbering from `auth-service` would collide
the moment another module adds its own `V1`. Timestamps avoid that without needing a shared
cross-module registry.

**Content:** each `CREATE TABLE` mirrors its entity's annotations exactly (names, nullability,
lengths, uniqueness, foreign keys) so that `ddl-auto: update` — left untouched for every other
table in the system — finds nothing to change on these three tables once Flyway has created them.
`roles.id` is `UUID` (matching `UserRoleEntity`, not the doc's `SERIAL`). `users.id` is
`VARCHAR` (ULID, matching `UserEntity`/`AppUser`, not the doc's DB-generated `UUID`).
One deliberate exception to "mirrors the entity": uniqueness of `users.username`, `users.email`
and `users.id_card_number_hash` is enforced only by partial unique indexes in the migration
(`CREATE UNIQUE INDEX ... WHERE deleted_at IS NULL`), so a soft-deleted user's username, email and
ID card can be reused (decision 3). `UserEntity` therefore declares no uniqueness on those columns —
`ddl-auto` would otherwise turn it into a full, all-rows constraint.

**Not migrated by Flyway:** role seed data stays in the existing `RoleDataInitializer`
`CommandLineRunner` (idempotent, checks `count() == 0`) — Flyway owns schema only, not seed rows,
to keep this change minimal.

**Operational note (not something this change can resolve by itself):** Flyway refuses to migrate
a non-empty schema that has no `flyway_schema_history` table, so any dev Postgres database previously
populated by `ddl-auto` will fail on next boot. Dropping just `users`/`roles`/`user_roles` is not
enough — other `ddl-auto` tables exist in the same schema (and `role_permission` references
`roles`). Reset by dropping and recreating the whole dev schema (e.g. `DROP SCHEMA public CASCADE;
CREATE SCHEMA public;`) once, before the first boot with these migrations. This repo has no
indication of real persisted data, so that is acceptable; it is a manual/infra step for whoever
runs the migration, not something scripted as part of this change. The same note is repeated as a
comment header in `V202610091200__create_roles_table.sql`.

## 7. Presentation layer changes

- **`UserRegisterRequest`** gains `idCardNumber`: `@NotNull`, `@Pattern(regexp = "^(\\d{9}|\\d{12})$")`
  — same style as the existing `username` field, and matching the domain VO's own validation as
  defense-in-depth.
- **`UserRegisterCommand`** gains `idCardNumber`. **`UserConverter`** passes it through.
- **`UserRegisterService`** adds a duplicate-ID-card check after the existing username/email checks:
  `existsByIdCardNumberHash(cryptoService.blindIndex(command.idCardNumber()))`, throwing
  `AuthValidationException` on conflict (new `AuthFieldName.ID_CARD_NUMBER` entry) — same pattern as
  the existing duplicate checks. Builds the `IdCardNumber` VO and passes it into `AppUser.create(...)`.
- **`UserProfileInfo`** (returned by `UserGetInfoUseCase`) gains `kycStatus` only — useful,
  non-sensitive. It does **not** expose `idCardNumber`, `idCardFrontUrl`, or `idCardBackUrl`; there
  is no reason to return sensitive PII/document links from an endpoint that isn't part of a KYC
  review flow. `LoginResponse` is unchanged.

## 8. Testing

Following TDD for each new piece:
- `IdCardNumber.create(...)` — accepts valid 9/12-digit strings, rejects everything else.
- `AesGcmCryptoService` — encrypt/decrypt round-trip recovers the original plaintext;
  `blindIndex(...)` is deterministic for the same input and differs for different input.
- `UserRegisterService` — rejects registration with a duplicate `idCardNumber` (by hash), same as
  the existing duplicate username/email tests.
- Soft-delete — `existsByUsername`/`existsByEmail`/`existsByIdCardNumberHash` ignore rows with
  `deletedAt` set.
- `CustomUserDetails` — `isEnabled()`/`isAccountNonLocked()` correctly reflect `LOCK`/`TERMINATED`/
  `DELETED` status.

Run `/code-review` on the diff before considering the change complete, per the project owner's
request.

## 9. Explicitly out of scope

- No KYC submission/review endpoints or admin workflow — only the data model that could support one
  later.
- No file-upload service for `idCardFrontUrl`/`idCardBackUrl` — they remain plain nullable URL
  strings with no validation.
- No migrations for `property-service`, `booking-service`, `listing-service`, or the
  `PERMISSION`/`ROLE_PERMISSION` tables.
- Pre-existing issues noticed during research but intentionally not touched here: other VOs
  (`Email`, `Username`, `Password`, `ProfileImage`) bypassing their own factory convention via
  `UserVOMapper`'s direct `new X(...)` calls instead of `from(...)`; `AuthJpaConfig`'s
  `@EntityScan`/`@EnableJpaRepositories` base packages not matching the real package layout;
  `application-dev.yml`/`application-prod.yml` being empty with no real per-environment
  datasource override. These are worth separate follow-ups, not bundled into this change.
- Role seed data stays in `RoleDataInitializer`, not moved into a Flyway migration.
