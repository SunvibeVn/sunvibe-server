# Auth User/Role Schema Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `users`/`roles`/`user_roles` SQL schema and the `modules/auth-service` Java code consistent with each other, add the missing identity-verification fields (`idCardNumber` encrypted at rest, `idCardFrontUrl`, `idCardBackUrl`, `kycStatus`), add soft-delete (`deletedAt`), wire account status into login, and introduce real Flyway migrations for these three tables.

**Architecture:** DDD + Hexagonal + CQRS, following `modules/auth-service`'s existing layering (domain → application → infrastructure → presentation). New domain concepts (`IdCardNumber` VO, `KycStatus` enum) stay framework-free; encryption is an infrastructure-only concern (a JPA `AttributeConverter` backed by a new `CryptoService` in `modules/common`) that the domain never sees.

**Tech Stack:** Java 21, Spring Boot (via `spring-boot-dependencies:4.1.1` BOM), Spring Data JPA + Hibernate, PostgreSQL + Flyway (`flyway-database-postgresql`), MapStruct 1.6.3, Lombok, JUnit 5 (JUnit Jupiter — the only test dependency currently on the classpath; no Mockito, so tests needing a double use a hand-written fake implementing the relevant interface).

**Spec:** `docs/superpowers/specs/2026-10-09-auth-user-schema-sync-design.md`

> **Historical record — partly superseded.** This plan is kept as it was executed. Where it says `idCardNumber` is required or its columns are `NOT NULL`, that is no longer true. It was superseded the same day by commit `2a5ab8a` ("Require only full name, phone and password at registration"): `idCardNumber` is **optional** at registration (validated and duplicate-checked only when provided), and migration `V202610091300__make_user_id_card_optional.sql` drops `NOT NULL` from `id_card_number` and `id_card_number_hash`. The current behaviour is described in `dev_docs/auth/01_AUTH_REGISTER_FLOW.md` and the current schema in `dev_docs/database/DATABASE_SCHEMA_POSTGRESQL.md`; the Flyway migrations are the source of truth.

## Global Constraints

- Scope is registration, login, and authentication only (`users`, `roles`, `user_roles`). No other business domain, no KYC review/admin workflow, no file-upload service.
- Only `idCardNumber` is a required registration input; `idCardFrontUrl`, `idCardBackUrl`, `kycStatus` exist in the schema/domain but are not collected or validated at registration (`kycStatus` defaults to `UNVERIFIED`).
  > **Superseded:** `idCardNumber` is now optional at registration; see the note at the top of this plan.
- `idCardNumber` is encrypted at rest (AES-256-GCM) via a new `CryptoService` in `modules/common` — no such utility exists today.
- A soft-deleted user's `username`/`email` must become reusable by a new registration — all existence/lookup queries exclude rows where `deletedAt IS NOT NULL`.
- `CustomUserDetails.isEnabled()`/`isAccountNonLocked()` must reflect `userStatus` (`DELETED`/`TERMINATED` → disabled, `LOCK` → locked) — today they silently return the `UserDetails` defaults regardless of status.
- `users.id` stays the application-generated ULID `String` (matching `UserId`/`AppUser`), **not** a DB-generated `UUID` — this also means fixing `UserJpaRepository extends JpaRepository<UserEntity, UUID>` to `<UserEntity, String>`.
- `roles.id` stays `UUID` (matching `UserRoleEntity`), **not** the doc's `SERIAL`.
- `email` stays nullable (matching the existing, intentional `UserEntity` comment "nullable – user may not be updated yet" and `UserRegisterRequest` having no `@NotNull` on email) — the design doc's `NOT NULL` is **not** followed here, since Java's existing intentional behavior wins per the spec's own rule ("map SQL to match the Java code's actual types/behavior").
- Flyway migrations live in `modules/auth-service/src/main/resources/db/migration/`, versioned by timestamp (`V{yyyyMMddHHmm}__description.sql`) — not sequential integers — because `classpath:db/migration` is shared across every module's JAR once packaged, and other modules (`property-service`, `booking-service`, `listing-service`) already have empty placeholder folders at that same path that could introduce a colliding `V1` later.
- `ddl-auto: update` stays untouched globally; migrations are written to exactly match the entity mappings so Hibernate finds nothing to alter on these three tables once Flyway has created them.
- Role seed data stays in the existing `RoleDataInitializer` runner — Flyway owns schema only, not seed rows.
- One deviation from the spec's literal wording, found during planning: the spec said to replace `AppUser.from(...)` with a `load(...)` factory and "update `UserMapper.toDomain` to call it." In reality `UserMapper` is a MapStruct interface that builds `AppUser` via its Lombok-generated no-arg constructor + setters (confirmed: `AppUser.from(...)` has zero callers anywhere in the repo) — MapStruct never calls `create()`/`from()` at all today. Rewriting `from()` into an equally-unused `load()` would just be new dead code. Task 3 instead **deletes** the dead `from()` method and leaves the MapStruct setter-based mapping as-is (pre-existing, out of this change's scope to rework) — `userStatus`, and the new fields, round-trip correctly either way because `@Mapping(target = "...", source = "...")` reads the entity's real persisted value, not a hardcoded one.
- No Testcontainers/real-Postgres test harness is introduced. The test profile (`application-test.yml`) deliberately runs against H2 with Flyway disabled; the three new migration files are therefore verified by inspection (they're written to mirror the entity annotations exactly) and by manually running the app against a real dev Postgres, not by the automated suite. This matches the existing test setup and avoids adding a new heavy dependency nobody asked for.
- **Operational note, not resolved by this plan:** if a dev Postgres database already has `users`/`roles`/`user_roles` tables (from prior `ddl-auto` runs), Flyway's `CREATE TABLE` migrations will fail on next boot because the tables already exist. Drop those three tables (or the whole dev DB) once before the first boot after this change — there's no real persisted data yet (repo is three commits in).

## Review Focus

- A registration request reusing an `idCardNumber` that's already registered (by a different user) — should be rejected the same way a duplicate username/email is, not silently accepted or a 500. → Task 6 test.
- A user who was soft-deleted (`deletedAt` set) — a new registration with that same username/email should succeed, not hit a stale uniqueness conflict. → Task 5 test.
- A user whose `userStatus` is `LOCK`, `TERMINATED`, or `DELETED` attempting to log in — must be rejected by Spring Security at authentication time, not allowed through with a valid password. → Task 7 test.
- Encrypting then decrypting the same `idCardNumber` — must recover the exact original plaintext, and two different plaintexts must never produce the same blind-index hash (which would silently merge two different people's duplicate-check identity). → Task 2 test.
- An `idCardNumber` input that isn't a valid 9 or 12-digit string (letters, wrong length, blank) — must be rejected by the domain VO with a clear validation error, not an `ArrayIndexOutOfBoundsException` or a silently-accepted bad value. → Task 1 test.

---

### Task 1: `KycStatus` enum and `IdCardNumber` value object

**Files:**
- Create: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/model/user/enums/KycStatus.java`
- Create: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/model/user/vo/IdCardNumber.java`
- Test: `modules/auth-service/src/test/java/vn/sunvibe/auth/core/domain/model/user/vo/IdCardNumberTest.java`

**Interfaces:**
- Produces: `KycStatus` enum with values `UNVERIFIED, PENDING, VERIFIED, REJECTED`. `IdCardNumber extends ValueObject<IdCardNumber>` with `IdCardNumber.create(String value)` (validates, throws `AuthValidationException` on bad input) and `IdCardNumber.from(String value)` (reconstruction, no validation), plus `getValue()`.

- [ ] **Step 1: Add the `ID_CARD_NUMBER` field name**

Modify `modules/auth-service/src/main/java/vn/sunvibe/auth/core/shared/enums/AuthFieldName.java` — add one enum constant after `COUNTRY_CODE`:

```java
    COUNTRY_CODE("countryCode", "Country Code"),
    ID_CARD_NUMBER("idCardNumber", "ID Card Number");
```

(Change the trailing `;` currently after `COUNTRY_CODE("countryCode", "Country Code")` to a `,` and add the new constant with the `;`.)

- [ ] **Step 2: Write the failing test for `IdCardNumber`**

```java
package vn.sunvibe.auth.core.domain.model.user.vo;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;

import static org.junit.jupiter.api.Assertions.*;

class IdCardNumberTest {

    @Test
    void create_acceptsNineDigitCmnd() {
        IdCardNumber idCardNumber = IdCardNumber.create("123456789");
        assertEquals("123456789", idCardNumber.getValue());
    }

    @Test
    void create_acceptsTwelveDigitCccd() {
        IdCardNumber idCardNumber = IdCardNumber.create("123456789012");
        assertEquals("123456789012", idCardNumber.getValue());
    }

    @Test
    void create_rejectsNonDigitCharacters() {
        assertThrows(AuthValidationException.class, () -> IdCardNumber.create("12345678A"));
    }

    @Test
    void create_rejectsWrongLength() {
        assertThrows(AuthValidationException.class, () -> IdCardNumber.create("12345"));
    }

    @Test
    void create_rejectsBlank() {
        assertThrows(AuthValidationException.class, () -> IdCardNumber.create(""));
    }

    @Test
    void create_rejectsNull() {
        assertThrows(AuthValidationException.class, () -> IdCardNumber.create(null));
    }

    @Test
    void from_doesNotValidate() {
        IdCardNumber idCardNumber = IdCardNumber.from("not-a-valid-number");
        assertEquals("not-a-valid-number", idCardNumber.getValue());
    }

    @Test
    void equalsAndHashCode_sameValueAreEqual() {
        assertEquals(IdCardNumber.create("123456789"), IdCardNumber.create("123456789"));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumberTest"`
Expected: FAIL to compile — `IdCardNumber` does not exist yet.

- [ ] **Step 4: Create `KycStatus`**

```java
package vn.sunvibe.auth.core.domain.model.user.enums;

/**
 * Identity-verification state of a user's submitted ID card.
 * New registrations always start {@code UNVERIFIED}; later KYC
 * review flows (out of scope here) move this to PENDING/VERIFIED/REJECTED.
 */
public enum KycStatus {

    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED

}
```

- [ ] **Step 5: Create `IdCardNumber`**

```java
package vn.sunvibe.auth.core.domain.model.user.vo;

import lombok.Getter;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.shared.ValueObject;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;

import java.util.regex.Pattern;

/**
 * A Vietnamese national ID card number: either the legacy 9-digit CMND
 * or the current 12-digit CCCD format. The plaintext value never leaves
 * this object's constructor validation — persistence-layer encryption
 * (see {@code EncryptedStringAttributeConverter}) is an infrastructure
 * concern this value object has no knowledge of.
 */
@Getter
public final class IdCardNumber extends ValueObject<IdCardNumber> {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{9}|\\d{12})$");

    private final String value;

    private IdCardNumber(String value) {
        this.value = value;
    }

    /** Build from new, user-supplied data — validates the format. */
    public static IdCardNumber create(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new AuthValidationException(AuthFieldName.ID_CARD_NUMBER,
                    "ID card number must be a 9-digit CMND or 12-digit CCCD.");
        }
        return new IdCardNumber(value);
    }

    /** Build from already-validated, persisted data — no re-validation. */
    public static IdCardNumber from(String value) {
        return new IdCardNumber(value);
    }

    @Override
    protected boolean equalsCore(IdCardNumber other) {
        return this.value.equals(other.value);
    }

    @Override
    protected int hashCodeCore() {
        return value.hashCode();
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.domain.model.user.vo.IdCardNumberTest"`
Expected: PASS (8 tests).

- [ ] **Step 7: Commit**

```bash
git add modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/model/user/enums/KycStatus.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/model/user/vo/IdCardNumber.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/shared/enums/AuthFieldName.java \
        modules/auth-service/src/test/java/vn/sunvibe/auth/core/domain/model/user/vo/IdCardNumberTest.java
git commit -m "Add KycStatus enum and IdCardNumber value object"
```

---

### Task 2: `CryptoService` (AES-GCM encryption + HMAC blind index)

**Files:**
- Create: `modules/common/src/main/java/vn/sunvibe/common/crypto/CryptoService.java`
- Create: `modules/common/src/main/java/vn/sunvibe/common/crypto/AesGcmCryptoService.java`
- Test: `modules/common/src/test/java/vn/sunvibe/common/crypto/AesGcmCryptoServiceTest.java`
- Modify: `app/src/main/resources/application.yml`

**Interfaces:**
- Produces: `CryptoService` interface — `String encrypt(String plaintext)`, `String decrypt(String ciphertext)`, `String blindIndex(String value)`. `AesGcmCryptoService implements CryptoService`, a `@Component`, constructor-takes two base64 key strings via `@Value`.

- [ ] **Step 1: Write the failing test**

```java
package vn.sunvibe.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmCryptoServiceTest {

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private final CryptoService cryptoService =
            new AesGcmCryptoService(randomBase64Key(), randomBase64Key());

    @Test
    void encryptThenDecrypt_recoversOriginalPlaintext() {
        String plaintext = "123456789012";
        String ciphertext = cryptoService.encrypt(plaintext);
        assertNotEquals(plaintext, ciphertext);
        assertEquals(plaintext, cryptoService.decrypt(ciphertext));
    }

    @Test
    void encrypt_isNonDeterministic() {
        String plaintext = "123456789012";
        assertNotEquals(cryptoService.encrypt(plaintext), cryptoService.encrypt(plaintext));
    }

    @Test
    void blindIndex_isDeterministicForSameInput() {
        assertEquals(cryptoService.blindIndex("123456789012"), cryptoService.blindIndex("123456789012"));
    }

    @Test
    void blindIndex_differsForDifferentInput() {
        assertNotEquals(cryptoService.blindIndex("123456789012"), cryptoService.blindIndex("123456789013"));
    }

    @Test
    void blindIndex_isHexEncodedSha256HmacLength() {
        // HMAC-SHA256 output is 32 bytes -> 64 hex characters
        assertEquals(64, cryptoService.blindIndex("123456789012").length());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:common:test --tests "vn.sunvibe.common.crypto.AesGcmCryptoServiceTest"`
Expected: FAIL to compile — `CryptoService`/`AesGcmCryptoService` don't exist yet.

- [ ] **Step 3: Create the `CryptoService` interface**

```java
package vn.sunvibe.common.crypto;

/**
 * Encrypts/decrypts a single sensitive string field at rest, and computes
 * a deterministic "blind index" for exact-match lookups against encrypted
 * data (since randomized encryption can't be compared or uniquely
 * constrained directly in SQL).
 */
public interface CryptoService {

    /** Encrypts {@code plaintext}; returns a base64 string, different every call. */
    String encrypt(String plaintext);

    /** Decrypts a value previously returned by {@link #encrypt(String)}. */
    String decrypt(String ciphertext);

    /**
     * Deterministic, keyed (HMAC) hash of {@code value}, hex-encoded.
     * Safe to store alongside the encrypted value and index/compare on,
     * without exposing the plaintext or being practically precomputable
     * the way an unkeyed hash of a small keyspace (e.g. ID card numbers)
     * would be.
     */
    String blindIndex(String value);
}
```

- [ ] **Step 4: Create `AesGcmCryptoService`**

```java
package vn.sunvibe.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM field encryption plus an HMAC-SHA256 blind index.
 *
 * <p>Two independent keys are used — one for encryption, one for the blind
 * index — so that a compromise of one key doesn't also compromise the
 * other property (confidentiality vs. searchability).
 */
@Component
public class AesGcmCryptoService implements CryptoService {

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec indexKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCryptoService(
            @Value("${app.crypto.id-card-key}") String base64EncryptionKey,
            @Value("${app.crypto.id-card-index-key}") String base64IndexKey) {
        this.encryptionKey = new SecretKeySpec(Base64.getDecoder().decode(base64EncryptionKey), "AES");
        this.indexKey = new SecretKeySpec(Base64.getDecoder().decode(base64IndexKey), "HmacSHA256");
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCiphertext = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
            System.arraycopy(encrypted, 0, ivAndCiphertext, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(ivAndCiphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt value", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] ivAndCiphertext = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] encrypted = new byte[ivAndCiphertext.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, iv.length);
            System.arraycopy(ivAndCiphertext, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt value", e);
        }
    }

    @Override
    public String blindIndex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(indexKey);
            byte[] hash = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute blind index", e);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :modules:common:test --tests "vn.sunvibe.common.crypto.AesGcmCryptoServiceTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Add the dev-only key config**

Modify `app/src/main/resources/application.yml` — append under the existing `app:` block (after `jwtRefreshExpirationMs: 604800000`):

```yaml
    jwtRefreshExpirationMs: 604800000
  crypto:
    # DEV-ONLY defaults. Production MUST override both via environment
    # variables — never rely on these committed values outside local dev.
    id-card-key: ${ID_CARD_CRYPTO_KEY:bRPKQTY8NxXKMp8utgjOlU9SR5TugGss37yotjTNq64=}
    id-card-index-key: ${ID_CARD_CRYPTO_INDEX_KEY:HM5Eo9BtYz7DTj8eSdV0Dn9CwAEgWoqjDFTxfcLXtvw=}
```

- [ ] **Step 7: Commit**

```bash
git add modules/common/src/main/java/vn/sunvibe/common/crypto/CryptoService.java \
        modules/common/src/main/java/vn/sunvibe/common/crypto/AesGcmCryptoService.java \
        modules/common/src/test/java/vn/sunvibe/common/crypto/AesGcmCryptoServiceTest.java \
        app/src/main/resources/application.yml
git commit -m "Add AES-GCM CryptoService with HMAC blind index for encrypted-field lookups"
```

---

### Task 3: `AppUser` aggregate — new fields, soft-delete, dead-code cleanup

**Files:**
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/model/user/aggregate/AppUser.java`
- Test: `modules/auth-service/src/test/java/vn/sunvibe/auth/core/domain/model/user/aggregate/AppUserTest.java`

**Interfaces:**
- Consumes: `IdCardNumber` (Task 1), `KycStatus` (Task 1).
- Produces: `AppUser.create(UserId, String fullName, Username, Email, Password, ProfileImage, IdCardNumber, Set<String> userRoles, Instant nowUtc)` (new signature — one extra `IdCardNumber` parameter after `profileImage`), `getIdCardNumber()`, `getIdCardFrontUrl()`, `getIdCardBackUrl()`, `getKycStatus()`, `getDeletedAt()` (all via existing Lombok `@Getter`/`@Setter`), `deactivate()` now also sets `deletedAt`.

- [ ] **Step 1: Write the failing test**

```java
package vn.sunvibe.auth.core.domain.model.user.aggregate;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;

import java.time.Instant;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class AppUserTest {

    private AppUser newUser() {
        return AppUser.create(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                Username.create("0912345678"),
                Email.create("a@example.com"),
                Password.create("hashed-password"),
                ProfileImage.create(null),
                IdCardNumber.create("123456789012"),
                new HashSet<>(),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void create_defaultsKycStatusToUnverified() {
        assertEquals(KycStatus.UNVERIFIED, newUser().getKycStatus());
    }

    @Test
    void create_leavesIdCardImagesAndDeletedAtNull() {
        AppUser user = newUser();
        assertNull(user.getIdCardFrontUrl());
        assertNull(user.getIdCardBackUrl());
        assertNull(user.getDeletedAt());
    }

    @Test
    void create_storesTheGivenIdCardNumber() {
        assertEquals("123456789012", newUser().getIdCardNumber().getValue());
    }

    @Test
    void deactivate_setsStatusDeletedAndDeletedAtTimestamp() {
        AppUser user = newUser();
        user.deactivate();
        assertEquals(UserStatus.DELETED, user.getUserStatus());
        assertNotNull(user.getDeletedAt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.domain.model.user.aggregate.AppUserTest"`
Expected: FAIL to compile — `AppUser.create(...)` doesn't accept an `IdCardNumber` yet, `getKycStatus()`/`getIdCardFrontUrl()`/etc. don't exist.

- [ ] **Step 3: Update `AppUser`**

In `modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/model/user/aggregate/AppUser.java`:

1. Add imports: `vn.sunvibe.auth.core.domain.model.user.enums.KycStatus` (the `vo.*` wildcard import already covers `IdCardNumber`).
2. Add five new fields alongside the existing ones (after `profileImage`, before `createdDate` — field order doesn't matter functionally, this keeps related identity fields together):

```java
    private IdCardNumber idCardNumber;

    private String idCardFrontUrl;

    private String idCardBackUrl;

    private KycStatus kycStatus;
```

   and add `deletedAt` alongside `lastLogin`:

```java
    private Instant deletedAt;
```

3. Replace the `create(...)` factory (and delete the dead `from(...)` method entirely — it has zero callers anywhere in the repo, confirmed by `grep -r "AppUser.from(" modules/`):

```java
    // Build from new data
    public static AppUser create(UserId id, String fullName, Username username, Email email, Password hashedPassword,
                                  ProfileImage profileImage, IdCardNumber idCardNumber, Set<String> userRoles, Instant nowUtc) {
        Instant now = nowUtc != null ? nowUtc : AppClock.currentUTC();
        AppUser appUser = new AppUser(id, fullName, username, email, hashedPassword, profileImage, now, now, userRoles, UserStatus.ACTIVE);
        appUser.idCardNumber = idCardNumber;
        appUser.kycStatus = KycStatus.UNVERIFIED;
        return appUser;
    }
```

   (The existing 4 hand-written constructors are untouched — none of them currently set the new fields, which is fine: they're only used to build a partially-populated `AppUser` before `create()`'s own assignments run. MapStruct's reconstruction path from `UserEntity` uses the no-arg constructor + setters, unaffected by this change — the new fields get set via new `@Mapping` entries added in Task 5.)

4. Update `deactivate()`:

```java
    public void deactivate() {
        this.userStatus = UserStatus.DELETED;
        this.deletedAt = AppClock.currentUTC();
        touch();
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.domain.model.user.aggregate.AppUserTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Fix the one existing caller of `AppUser.create(...)`**

`UserRegisterService` (Task 6) is the only caller — it's updated there, not here, since it also needs Task 1/Task 2's types wired in together. Run a repo-wide check now to confirm no other caller was missed:

Run: `grep -rn "AppUser.create(" modules/`
Expected: exactly one match, in `UserRegisterService.java` (will fail to compile until Task 6 — that's expected and resolved there).

- [ ] **Step 6: Commit**

```bash
git add modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/model/user/aggregate/AppUser.java \
        modules/auth-service/src/test/java/vn/sunvibe/auth/core/domain/model/user/aggregate/AppUserTest.java
git commit -m "Add idCardNumber/kycStatus/deletedAt to AppUser; remove dead from() factory"
```

*(Note: the full module won't compile again until Task 6 updates `UserRegisterService`'s call site — that's expected mid-plan; each task's own test target compiles and passes in isolation via `--tests`, and a full `./gradlew compileJava` is run at the end of Task 6 and again as the final verification.)*

---

### Task 4: `UserEntity`, encrypted-field converter, and Flyway migrations

**Files:**
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/entity/user/UserEntity.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/entity/user/UserRoleEntity.java`
- Create: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/converter/manual/EncryptedStringAttributeConverter.java`
- Create: `modules/auth-service/src/main/resources/db/migration/V202610091200__create_roles_table.sql`
- Create: `modules/auth-service/src/main/resources/db/migration/V202610091201__create_users_table.sql`
- Create: `modules/auth-service/src/main/resources/db/migration/V202610091202__create_user_roles_table.sql`

**Interfaces:**
- Consumes: `CryptoService` (Task 2).
- Produces: `UserEntity` fields `idCardNumber` (`String`, converter-encrypted), `idCardNumberHash` (`String`, plain), `idCardFrontUrl`, `idCardBackUrl` (`String`), `kycStatus` (`KycStatus`), `deletedAt` (`Instant`). `UserRoleEntity`'s table is finalized as `ROLES` (the pending uncommitted rename is kept and committed here).

This task has no new pure-Java logic of its own to unit test (it's JPA mapping + raw SQL) — its correctness is verified by compilation, by Task 5's persistence-adapter test (which exercises the full entity mapping including this converter), and by manual review against the entity annotations below. There is no failing-test step here; proceed directly to the implementation.

- [ ] **Step 1: Finalize the `roles` table name**

`UserRoleEntity.java` currently has an uncommitted change from `@Table(name = "ROLE")` to `@Table(name = "ROLES")`. Confirm it reads:

```java
@Table(name = "ROLES")
```

(No action needed if it already does — just verify before writing the migration below, since the migration's table name must match exactly.)

- [ ] **Step 2: Create `EncryptedStringAttributeConverter`**

```java
package vn.sunvibe.auth.core.infrastructure.persistence.converter.manual;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.sunvibe.common.crypto.CryptoService;

/**
 * Transparently encrypts a JPA entity field at rest and decrypts it on
 * read, delegating to {@link CryptoService}. Applied per-field via
 * {@code @Convert(converter = EncryptedStringAttributeConverter.class)}
 * — Spring Boot's JPA auto-configuration resolves {@code @Convert}
 * converters as Spring beans, so this class's {@link CryptoService}
 * dependency is injected normally.
 */
@Converter(autoApply = false)
@Component
@RequiredArgsConstructor
public class EncryptedStringAttributeConverter implements AttributeConverter<String, String> {

    private final CryptoService cryptoService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : cryptoService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : cryptoService.decrypt(dbData);
    }
}
```

- [ ] **Step 3: Update `UserEntity`**

Add imports: `jakarta.persistence.Convert`, `vn.sunvibe.auth.core.domain.model.user.enums.KycStatus`, `vn.sunvibe.auth.core.infrastructure.persistence.converter.manual.EncryptedStringAttributeConverter`.

Make the three existing columns explicitly required, matching how they're actually used (fix the current implicit/inconsistent nullability):

```java
    /**
     * Full name
     */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /**
     * Username (VO → String)
     */
    @Column(nullable = false, length = 32, unique = true)
    private String username;

    /**
     * Email (VO → String, lower-case). Stays nullable — a user may not
     * have one set yet.
     */
    @Column(name = "email", length = 100, unique = true)
    private String email;
```

Add the five new columns after `profileImage`:

> **Superseded:** the shipped `UserEntity` maps `id_card_number` and `id_card_number_hash` as nullable and declares no uniqueness (`unique = true` would make `ddl-auto` create an all-rows constraint); uniqueness among non-deleted users comes from the partial unique index in the migration.

```java
    /**
     * ID card number (CMND/CCCD), encrypted at rest.
     */
    @Column(name = "id_card_number", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringAttributeConverter.class)
    private String idCardNumber;

    /**
     * Deterministic HMAC blind index of {@link #idCardNumber}, used only
     * for the duplicate-registration lookup — never decrypted/compared
     * to plaintext directly.
     */
    @Column(name = "id_card_number_hash", nullable = false, length = 64, unique = true)
    private String idCardNumberHash;

    @Column(name = "id_card_front_url", columnDefinition = "TEXT")
    private String idCardFrontUrl;

    @Column(name = "id_card_back_url", columnDefinition = "TEXT")
    private String idCardBackUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;
```

Add `deletedAt` after `lastLogin`:

```java
    @Column(name = "deleted_at")
    private Instant deletedAt;
```

- [ ] **Step 4: Create the `roles` migration**

```sql
-- V202610091200__create_roles_table.sql
CREATE TABLE roles (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    CONSTRAINT uk_roles_name UNIQUE (name)
);
```

- [ ] **Step 5: Create the `users` migration**

> **Superseded:** the shipped `V202610091201__create_users_table.sql` differs from the block below: its unique indexes are partial (`WHERE deleted_at IS NULL`, so a soft-deleted user's username, e-mail and ID card can be reused) and it has no `uk_users_id_card_number_hash` constraint. Its `NOT NULL` on `id_card_number` / `id_card_number_hash` is then dropped by `V202610091300__make_user_id_card_optional.sql`.

```sql
-- V202610091201__create_users_table.sql
CREATE TABLE users (
    id VARCHAR(26) NOT NULL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(32) NOT NULL,
    email VARCHAR(100),
    password VARCHAR(255) NOT NULL,
    profile_image VARCHAR(500),
    id_card_number TEXT NOT NULL,
    id_card_number_hash VARCHAR(64) NOT NULL,
    id_card_front_url TEXT,
    id_card_back_url TEXT,
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    user_status VARCHAR(20) NOT NULL,
    created_date TIMESTAMP WITH TIME ZONE NOT NULL,
    last_update TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_users_id_card_number_hash UNIQUE (id_card_number_hash)
);

CREATE UNIQUE INDEX idx_users_username ON users (username);
CREATE UNIQUE INDEX idx_users_email ON users (email);
```

- [ ] **Step 6: Create the `user_roles` migration**

```sql
-- V202610091202__create_user_roles_table.sql
CREATE TABLE user_roles (
    user_id VARCHAR(26) NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
```

- [ ] **Step 7: Compile to verify the entity/converter wiring is valid**

Run: `./gradlew :modules:auth-service:compileJava`
Expected: FAILS — `UserRepositoryAdapter`/`UserMapper`/`UserRegisterService` still call the old `AppUser.create(...)` signature and don't know about the new entity fields yet. This is expected; it's resolved in Tasks 5–6. Confirm the failure is specifically about those call sites and not about `UserEntity`, `EncryptedStringAttributeConverter`, or the migration files themselves (those three should show no errors).

- [ ] **Step 8: Commit**

```bash
git add modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/entity/user/UserEntity.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/entity/user/UserRoleEntity.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/converter/manual/EncryptedStringAttributeConverter.java \
        modules/auth-service/src/main/resources/db/migration/
git commit -m "Add encrypted id-card fields to UserEntity and Flyway migrations for users/roles/user_roles"
```

---

### Task 5: Wire persistence — mappers, repositories, soft-delete filtering

**Files:**
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/converter/auto/UserMapper.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/converter/auto/UserVOMapper.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/repository/user/UserJpaRepository.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/repository/user/UserDomainRepository.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/adapter/user/UserRepositoryAdapter.java`
- Test: `modules/auth-service/src/test/java/vn/sunvibe/auth/core/infrastructure/persistence/repository/user/UserJpaRepositorySoftDeleteTest.java`

**Interfaces:**
- Consumes: `CryptoService` (Task 2), `IdCardNumber`/`KycStatus` (Task 1), `UserEntity` new fields (Task 4).
- Produces: `UserDomainRepository.existsByIdCardNumberHash(String hash)`; `UserRepositoryAdapter`/`UserJpaRepository` exclude soft-deleted rows from every existence/lookup query.

- [ ] **Step 1: Write the failing repository test**

```java
package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class UserJpaRepositorySoftDeleteTest {

    @Autowired
    private UserJpaRepository userJpaRepository;

    private UserEntity buildUser(String id, String username, String email, Instant deletedAt) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setFullName("Nguyen Van A");
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setPassword("hashed-password");
        entity.setIdCardNumber("123456789012");
        entity.setIdCardNumberHash("hash-" + id);
        entity.setKycStatus(KycStatus.UNVERIFIED);
        entity.setUserStatus(UserStatus.ACTIVE);
        entity.setCreatedDate(Instant.now());
        entity.setLastUpdate(Instant.now());
        entity.setDeletedAt(deletedAt);
        return entity;
    }

    @Test
    void existsByUsername_ignoresSoftDeletedRow() {
        userJpaRepository.save(buildUser("id-1", "0912345678", "a@example.com", Instant.now()));

        assertFalse(userJpaRepository.existsByUsername("0912345678"));
    }

    @Test
    void existsByUsername_findsNonDeletedRow() {
        userJpaRepository.save(buildUser("id-2", "0987654321", "b@example.com", null));

        assertTrue(userJpaRepository.existsByUsername("0987654321"));
    }

    @Test
    void existsByEmail_ignoresSoftDeletedRow() {
        userJpaRepository.save(buildUser("id-3", "0911111111", "c@example.com", Instant.now()));

        assertFalse(userJpaRepository.existsByEmail("c@example.com"));
    }

    @Test
    void existsByIdCardNumberHash_ignoresSoftDeletedRow() {
        UserEntity deleted = buildUser("id-4", "0922222222", "d@example.com", Instant.now());
        deleted.setIdCardNumberHash("shared-hash");
        userJpaRepository.save(deleted);

        assertFalse(userJpaRepository.existsByIdCardNumberHash("shared-hash"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepositorySoftDeleteTest"`
Expected: FAIL to compile — `existsByIdCardNumberHash` doesn't exist yet, and the current `existsByUsername`/`existsByEmail` don't filter `deletedAt`.

- [ ] **Step 3: Update `UserJpaRepository`**

```java
package vn.sunvibe.auth.core.infrastructure.persistence.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;

import java.util.Optional;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, String> {

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u " +
            "WHERE u.username = :username AND u.deletedAt IS NULL")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u " +
            "WHERE u.email = :email AND u.deletedAt IS NULL")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UserEntity u " +
            "WHERE u.idCardNumberHash = :idCardNumberHash AND u.deletedAt IS NULL")
    boolean existsByIdCardNumberHash(@Param("idCardNumberHash") String idCardNumberHash);

    @Query("SELECT u FROM UserEntity u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<UserEntity> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles " +
            "WHERE u.username = :username AND u.deletedAt IS NULL")
    Optional<UserEntity> findByUsername(@Param("username") String username);

}
```

(Note the `JpaRepository<UserEntity, String>` fix — was `UUID`, which never matched `UserEntity.id`'s actual `String` type.)

- [ ] **Step 4: Add `existsByIdCardNumberHash` to the domain port**

```java
package vn.sunvibe.auth.core.domain.repository.user;


import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;

public interface UserDomainRepository {

    boolean existsByUsername(String userName);

    boolean existsByEmail(String email);

    boolean existsByIdCardNumberHash(String idCardNumberHash);

    AppUser save(AppUser user);

    AppUser findByUsername(String username);

    AppUser findByEmail(String email);
}
```

- [ ] **Step 5: Update `UserVOMapper`**

Add these two named methods (anywhere among the other `@Named` methods — e.g. right after `mapProfileImage`/`toProfileImage`):

```java
    @Named("mapIdCardNumber")
    default String mapIdCardNumber(IdCardNumber idCardNumber) {
        return idCardNumber.getValue();
    }
```

and in the "ENTITY → DOMAIN" section:

```java
    @Named("toIdCardNumber")
    default IdCardNumber toIdCardNumber(String value) {
        return IdCardNumber.from(value);
    }
```

(`IdCardNumber` is already covered by the existing `vn.sunvibe.auth.core.domain.model.user.vo.*` wildcard import.)

- [ ] **Step 6: Update `UserMapper`**

Add to `toDomain(...)`:

```java
    @Mapping(target = "idCardNumber", source = "idCardNumber", qualifiedByName = "toIdCardNumber")
    @Mapping(target = "idCardFrontUrl", source = "idCardFrontUrl")
    @Mapping(target = "idCardBackUrl", source = "idCardBackUrl")
    @Mapping(target = "kycStatus", source = "kycStatus")
    @Mapping(target = "deletedAt", source = "deletedAt")
```

(alongside the existing `@Mapping` lines, before `AppUser toDomain(UserEntity userEntity);`).

Add to `toEntity(...)`:

```java
    @Mapping(target = "idCardNumber", source = "idCardNumber", qualifiedByName = "mapIdCardNumber")
    @Mapping(target = "idCardFrontUrl", source = "idCardFrontUrl")
    @Mapping(target = "idCardBackUrl", source = "idCardBackUrl")
    @Mapping(target = "kycStatus", source = "kycStatus")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "idCardNumberHash", ignore = true)
```

(`idCardNumberHash` has no domain-side source — it's computed by `UserRepositoryAdapter` below — so MapStruct must be told explicitly to ignore it, or it fails the build with an unmapped-target-property error.)

- [ ] **Step 7: Update `UserRepositoryAdapter`**

```java
package vn.sunvibe.auth.core.infrastructure.persistence.adapter.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.domain.exception.user.UserNotFoundException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.auto.UserMapper;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepository;
import vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserRoleJpaRepository;
import vn.sunvibe.auth.core.shared.constant.UserMessage;
import vn.sunvibe.common.crypto.CryptoService;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserDomainRepository {

    private final UserJpaRepository userJpaRepository;
    private final UserRoleJpaRepository userRoleJpaRepository;
    private final UserMapper userMapper;
    private final CryptoService cryptoService;

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByIdCardNumberHash(String idCardNumberHash) {
        return userJpaRepository.existsByIdCardNumberHash(idCardNumberHash);
    }

    @Override
    public AppUser save(AppUser appUser) {
        UserEntity userEntity = userMapper.toEntity(appUser);
        userEntity.setIdCardNumberHash(cryptoService.blindIndex(appUser.getIdCardNumber().getValue()));

        // Get role from DB by input
        Set<UserRoleEntity> userRoleEntities = userRoleJpaRepository.findByNameIn(appUser.getUserRoles());
        userEntity.setUserRoles(userRoleEntities);

        return userMapper.toDomain(userJpaRepository.save(userEntity));
    }

    @Override
    public AppUser findByUsername(String username) {
        UserEntity userEntity = userJpaRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(String.format(UserMessage.USER_NOT_FOUND_WITH_USERNAME, username)));
        return userMapper.toDomain(userEntity);
    }

    @Override
    public AppUser findByEmail(String email) {
        UserEntity userEntity = userJpaRepository.findByEmail(email).orElseThrow(() -> new UserNotFoundException(String.format(UserMessage.USER_NOT_FOUND_WITH_EMAIL, email)));
        return userMapper.toDomain(userEntity);
    }

}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.infrastructure.persistence.repository.user.UserJpaRepositorySoftDeleteTest"`
Expected: PASS (4 tests).

- [ ] **Step 9: Commit**

```bash
git add modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/converter/auto/UserMapper.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/converter/auto/UserVOMapper.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/repository/user/UserJpaRepository.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/domain/repository/user/UserDomainRepository.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/persistence/adapter/user/UserRepositoryAdapter.java \
        modules/auth-service/src/test/java/vn/sunvibe/auth/core/infrastructure/persistence/repository/user/UserJpaRepositorySoftDeleteTest.java
git commit -m "Wire idCardNumber/kyc/soft-delete through mappers and repositories; fix UserJpaRepository id type"
```

---

### Task 6: Registration flow — require `idCardNumber`, reject duplicates

> **Superseded:** duplicate rejection still applies, but `idCardNumber` is no longer required: blank or missing means "not provided" and skips the format and duplicate checks.

**Files:**
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/presentation/dto/request/user/UserRegisterRequest.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/application/command/user/UserRegisterCommand.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/presentation/converter/user/UserConverter.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/application/usecase/user/impl/UserRegisterService.java`
- Test: `modules/auth-service/src/test/java/vn/sunvibe/auth/core/application/usecase/user/impl/UserRegisterServiceTest.java`

**Interfaces:**
- Consumes: `UserDomainRepository.existsByIdCardNumberHash` (Task 5), `CryptoService.blindIndex` (Task 2), `IdCardNumber.create` (Task 1), `AppUser.create(...)` new signature (Task 3).

- [ ] **Step 1: Write the failing test**

This test uses a hand-written fake for `UserDomainRepository` (no Mockito on the classpath):

```java
package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.common.crypto.AesGcmCryptoService;
import vn.sunvibe.common.crypto.CryptoService;
import vn.sunvibe.common.generator.id.IdGenerator;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserRegisterServiceTest {

    /**
     * Minimal in-memory fake — Mockito isn't on this module's classpath.
     * Mirrors the real {@code UserRepositoryAdapter}: it hashes the
     * idCardNumber with the same {@link CryptoService} the service under
     * test uses, so a later {@code existsByIdCardNumberHash} lookup with
     * a freshly-computed hash actually matches what {@code save} stored.
     */
    private static class FakeUserDomainRepository implements UserDomainRepository {
        private final CryptoService cryptoService;
        private final Set<String> usernames = new HashSet<>();
        private final Set<String> emails = new HashSet<>();
        private final Set<String> idCardNumberHashes = new HashSet<>();
        private final Map<String, AppUser> saved = new HashMap<>();

        private FakeUserDomainRepository(CryptoService cryptoService) {
            this.cryptoService = cryptoService;
        }

        @Override
        public boolean existsByUsername(String userName) {
            return usernames.contains(userName);
        }

        @Override
        public boolean existsByEmail(String email) {
            return emails.contains(email);
        }

        @Override
        public boolean existsByIdCardNumberHash(String idCardNumberHash) {
            return idCardNumberHashes.contains(idCardNumberHash);
        }

        @Override
        public AppUser save(AppUser user) {
            usernames.add(user.getUsername().getValue());
            emails.add(user.getEmail().getValue());
            idCardNumberHashes.add(cryptoService.blindIndex(user.getIdCardNumber().getValue()));
            saved.put(user.getUsername().getValue(), user);
            return user;
        }

        @Override
        public AppUser findByUsername(String username) {
            return saved.get(username);
        }

        @Override
        public AppUser findByEmail(String email) {
            throw new UnsupportedOperationException();
        }
    }

    private FakeUserDomainRepository repository;
    private UserRegisterService service;

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @BeforeEach
    void setUp() {
        CryptoService cryptoService = new AesGcmCryptoService(randomBase64Key(), randomBase64Key());
        repository = new FakeUserDomainRepository(cryptoService);
        PasswordEncoder passwordEncoder = new PasswordEncoder() {
            @Override
            public String encode(CharSequence rawPassword) {
                return "hashed:" + rawPassword;
            }

            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return encode(rawPassword).equals(encodedPassword);
            }
        };
        IdGenerator<String> ulidGenerator = () -> "01ARZ3NDEKTSV4RRFFQ69G5FAV";
        service = new UserRegisterService(repository, passwordEncoder, ulidGenerator, cryptoService);
    }

    private UserRegisterCommand command(String username, String email, String idCardNumber) {
        return UserRegisterCommand.builder()
                .fullName("Nguyen Van A")
                .username(username)
                .email(email)
                .password("P@ssw0rd!")
                .idCardNumber(idCardNumber)
                .roles(null)
                .build();
    }

    @Test
    void execute_registersUserWithRequiredIdCardNumber() {
        service.execute(command("0912345678", "a@example.com", "123456789012"), "fingerprint");

        AppUser saved = repository.findByUsername("0912345678");
        assertNotNull(saved);
        assertEquals("123456789012", saved.getIdCardNumber().getValue());
    }

    @Test
    void execute_rejectsDuplicateIdCardNumber() {
        service.execute(command("0912345678", "a@example.com", "123456789012"), "fingerprint");

        AuthValidationException exception = assertThrows(AuthValidationException.class,
                () -> service.execute(command("0987654321", "b@example.com", "123456789012"), "fingerprint"));
        assertEquals(AuthFieldName.ID_CARD_NUMBER, exception.getField());
    }

    @Test
    void execute_rejectsInvalidIdCardNumberFormat() {
        assertThrows(AuthValidationException.class,
                () -> service.execute(command("0912345678", "a@example.com", "not-a-number"), "fingerprint"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.application.usecase.user.impl.UserRegisterServiceTest"`
Expected: FAIL to compile — `UserRegisterCommand.builder().idCardNumber(...)` doesn't exist, `AuthValidationException.getField()` needs checking (confirm it already exists — it does, per the existing class), `UserDomainRepository` fake won't compile against the pre-Task-5 interface shape (should already be fine since Task 5 landed first).

- [ ] **Step 3: Update `UserRegisterCommand`**

```java
package vn.sunvibe.auth.core.application.command.user;

import lombok.Builder;

import java.util.Set;

@Builder
public record UserRegisterCommand(
        String fullName,
        String username,
        String email,
        String password,
        String idCardNumber,
        Set<String> roles
) {
}
```

- [ ] **Step 4: Update `UserRegisterRequest`**

Add the field (after `email`, before `password` — matching the order of the other fields):

```java
        @Size(min = 11, max = 100, message = "From 11 to 100 characters in length.")
        String email,

        @NotNull(message = "ID card number is mandatory.")
        @Pattern(
                regexp = "^(\\d{9}|\\d{12})$",
                message = "ID card number must be a 9-digit CMND or 12-digit CCCD."
        )
        String idCardNumber,

        @StrongPassword
```

- [ ] **Step 5: Update `UserConverter`**

```java
    public UserRegisterCommand toUserRegisterCommand(UserRegisterRequest userRegisterRequest) {
        return UserRegisterCommand.builder()
                .fullName(userRegisterRequest.fullName())
                .username(userRegisterRequest.username())
                .email(userRegisterRequest.email())
                .password(userRegisterRequest.password())
                .idCardNumber(userRegisterRequest.idCardNumber())
                .roles(userRegisterRequest.roles())
                .build();
    }
```

- [ ] **Step 6: Update `UserRegisterService`**

```java
package vn.sunvibe.auth.core.application.usecase.user.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.sunvibe.auth.core.application.command.user.UserRegisterCommand;
import vn.sunvibe.auth.core.application.usecase.user.UserRegisterUseCase;
import vn.sunvibe.auth.core.domain.exception.share.AuthValidationException;
import vn.sunvibe.auth.core.shared.enums.AuthFieldName;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.RoleName;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.common.crypto.CryptoService;
import vn.sunvibe.common.generator.id.IdGenerator;
import vn.sunvibe.common.time.AppClock;

import java.util.HashSet;
import java.util.Set;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserRegisterService implements UserRegisterUseCase {

    private final UserDomainRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final IdGenerator<String> ulidGenerator;
    private final CryptoService cryptoService;

    @Transactional
    @Override
    public void execute(UserRegisterCommand userRegisterCommand, String rawFingerprint) {

        // Check username exists
        if (userRepository.existsByUsername(userRegisterCommand.username())) {
            throw new AuthValidationException(AuthFieldName.USER_NAME, "Phone number is already in use.");
        }
        // Check email exists
        if (userRepository.existsByEmail(userRegisterCommand.email())) {
            throw new AuthValidationException(AuthFieldName.EMAIL, "Email is already in use.");
        }
        // Check ID card number exists
        String idCardNumberHash = cryptoService.blindIndex(userRegisterCommand.idCardNumber());
        if (userRepository.existsByIdCardNumberHash(idCardNumberHash)) {
            throw new AuthValidationException(AuthFieldName.ID_CARD_NUMBER, "ID card number is already in use.");
        }

        // Build Value-Object
        UserId userId = UserId.create(ulidGenerator.generateId());
        Username usernameVO = Username.create(userRegisterCommand.username());
        Email emailVO = Email.create(userRegisterCommand.email());
        Password passwordVO = Password.create(passwordEncoder.encode(userRegisterCommand.password())); // Encode password
        ProfileImage profileImageVO = ProfileImage.create(null);
        IdCardNumber idCardNumberVO = IdCardNumber.create(userRegisterCommand.idCardNumber());

        // Create Aggregate with no roles, then assign requested roles through the
        // domain guard that rejects privileged roles (ADMIN / SUPER_ADMIN)
        AppUser appUser = AppUser.create(userId, userRegisterCommand.fullName(), usernameVO, emailVO, passwordVO, profileImageVO, idCardNumberVO, new HashSet<>(), AppClock.currentUTC());

        Set<String> requestedRoles = userRegisterCommand.roles();
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            appUser.assignRole(RoleName.USER.name());
        } else {
            requestedRoles.forEach(appUser::assignRole);
        }

        // Save in database
        appUser = userRepository.save(appUser);

        log.info("Register user successful: {}", appUser.getUsername().getValue());

        // Publish event
//        eventPublisher.publish(new UserRegisteredEvent(user.getId(), user.getUsername().getValue(), user.getEmail().getValue(), LocalDateTime.now()), "Create user");

    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.application.usecase.user.impl.UserRegisterServiceTest"`
Expected: PASS (3 tests).

- [ ] **Step 8: Compile the whole module**

Run: `./gradlew :modules:auth-service:compileJava :modules:common:compileJava`
Expected: PASS — this is the point where every call site touched since Task 3 (`AppUser.create`) is now consistent. Fix any remaining compile errors before moving on.

- [ ] **Step 9: Commit**

```bash
git add modules/auth-service/src/main/java/vn/sunvibe/auth/core/presentation/dto/request/user/UserRegisterRequest.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/application/command/user/UserRegisterCommand.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/presentation/converter/user/UserConverter.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/application/usecase/user/impl/UserRegisterService.java \
        modules/auth-service/src/test/java/vn/sunvibe/auth/core/application/usecase/user/impl/UserRegisterServiceTest.java
git commit -m "Require idCardNumber at registration and reject duplicates"
```

---

### Task 7: Enforce account status at login

**Files:**
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/security/userdetails/CustomUserDetails.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/security/provider/CustomUserDetailsService.java`
- Test: `modules/auth-service/src/test/java/vn/sunvibe/auth/core/infrastructure/security/userdetails/CustomUserDetailsTest.java`

**Interfaces:**
- Consumes: `UserStatus` enum (existing: `ACTIVE, LOCK, TERMINATED, PENDING, DELETED`).
- Produces: `CustomUserDetails.setUserStatus(UserStatus)` / `getUserStatus()`; `isEnabled()` and `isAccountNonLocked()` now reflect it.

- [ ] **Step 1: Write the failing test**

```java
package vn.sunvibe.auth.core.infrastructure.security.userdetails;

import org.junit.jupiter.api.Test;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CustomUserDetailsTest {

    private CustomUserDetails detailsWithStatus(UserStatus status) {
        CustomUserDetails details = new CustomUserDetails();
        details.setUserId("id");
        details.setUsername("0912345678");
        details.setPassword("hashed");
        details.setEmail("a@example.com");
        details.setAuthorities(Set.of());
        details.setUserStatus(status);
        return details;
    }

    @Test
    void isEnabled_falseWhenDeleted() {
        assertFalse(detailsWithStatus(UserStatus.DELETED).isEnabled());
    }

    @Test
    void isEnabled_falseWhenTerminated() {
        assertFalse(detailsWithStatus(UserStatus.TERMINATED).isEnabled());
    }

    @Test
    void isEnabled_trueWhenActive() {
        assertTrue(detailsWithStatus(UserStatus.ACTIVE).isEnabled());
    }

    @Test
    void isAccountNonLocked_falseWhenLocked() {
        assertFalse(detailsWithStatus(UserStatus.LOCK).isAccountNonLocked());
    }

    @Test
    void isAccountNonLocked_trueWhenActive() {
        assertTrue(detailsWithStatus(UserStatus.ACTIVE).isAccountNonLocked());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetailsTest"`
Expected: FAIL to compile — `setUserStatus`/`getUserStatus` don't exist yet, and the current `isEnabled()`/`isAccountNonLocked()` always return the `UserDetails` defaults (`true`).

- [ ] **Step 3: Update `CustomUserDetails`**

```java
package vn.sunvibe.auth.core.infrastructure.security.userdetails;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;

import java.util.Collection;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private String userId;
    private String username;
    private String password;
    private String email;
    private Collection<? extends GrantedAuthority> authorities;
    private UserStatus userStatus;

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return userStatus != UserStatus.LOCK;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return userStatus != UserStatus.DELETED && userStatus != UserStatus.TERMINATED;
    }

}
```

- [ ] **Step 4: Update `CustomUserDetailsService`**

Add one line to `mapUserToCustomUserDetails(...)`:

```java
    private CustomUserDetails mapUserToCustomUserDetails(AppUser user, Set<SimpleGrantedAuthority> authorities) {
        CustomUserDetails customUserDetails = new CustomUserDetails();
        customUserDetails.setUserId(user.getId().getValue());
        customUserDetails.setUsername(user.getUsername().getValue());
        customUserDetails.setEmail(user.getEmail().getValue());
        customUserDetails.setPassword(user.getPassword().getHashedValue());
        customUserDetails.setAuthorities(authorities);
        customUserDetails.setUserStatus(user.getUserStatus());

//        log.info("CustomUserDetailsService.mapUserToCustomUserDetails");
        return customUserDetails;
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.infrastructure.security.userdetails.CustomUserDetailsTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/security/userdetails/CustomUserDetails.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/infrastructure/security/provider/CustomUserDetailsService.java \
        modules/auth-service/src/test/java/vn/sunvibe/auth/core/infrastructure/security/userdetails/CustomUserDetailsTest.java
git commit -m "Block LOCK/TERMINATED/DELETED users at login via UserDetails status checks"
```

---

### Task 8: Expose `kycStatus` on the user profile endpoint

**Files:**
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/presentation/dto/response/user/UserProfileInfo.java`
- Modify: `modules/auth-service/src/main/java/vn/sunvibe/auth/core/application/usecase/user/impl/UserGetInfoService.java`
- Test: `modules/auth-service/src/test/java/vn/sunvibe/auth/core/application/usecase/user/impl/UserGetInfoServiceTest.java`

**Interfaces:**
- Consumes: `AppUser.getKycStatus()` (Task 3).
- Produces: `UserProfileInfo.kycStatus()` (`String`, the enum's name — matching how every other field here is a plain `String`, not a typed enum, since this is a wire-format DTO).

- [ ] **Step 1: Write the failing test**

This needs a security context, so it stubs `SecurityContextHolder` directly rather than starting a Spring context:

```java
package vn.sunvibe.auth.core.application.usecase.user.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.sunvibe.auth.core.domain.model.user.aggregate.AppUser;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.vo.*;
import vn.sunvibe.auth.core.domain.repository.user.UserDomainRepository;
import vn.sunvibe.auth.core.presentation.dto.response.user.UserProfileInfo;

import java.time.Instant;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class UserGetInfoServiceTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void execute_includesKycStatus() {
        AppUser appUser = AppUser.create(
                UserId.create("01ARZ3NDEKTSV4RRFFQ69G5FAV"),
                "Nguyen Van A",
                Username.create("0912345678"),
                Email.create("a@example.com"),
                Password.create("hashed-password"),
                ProfileImage.create(null),
                IdCardNumber.create("123456789012"),
                new HashSet<>(),
                Instant.now());

        UserDomainRepository repository = new UserDomainRepository() {
            @Override
            public boolean existsByUsername(String userName) { return false; }
            @Override
            public boolean existsByEmail(String email) { return false; }
            @Override
            public boolean existsByIdCardNumberHash(String idCardNumberHash) { return false; }
            @Override
            public AppUser save(AppUser user) { return user; }
            @Override
            public AppUser findByUsername(String username) { return appUser; }
            @Override
            public AppUser findByEmail(String email) { throw new UnsupportedOperationException(); }
        };

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("0912345678", null));

        UserGetInfoService service = new UserGetInfoService(repository);
        UserProfileInfo profile = service.execute();

        assertEquals(KycStatus.UNVERIFIED.name(), profile.kycStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.application.usecase.user.impl.UserGetInfoServiceTest"`
Expected: FAIL — `UserProfileInfo.kycStatus()` doesn't exist yet.

- [ ] **Step 3: Update `UserProfileInfo`**

```java
package vn.sunvibe.auth.core.presentation.dto.response.user;

import lombok.Builder;

@Builder
public record UserProfileInfo(
        String fullName,
        String username,
        String email,
        String profileImageUrl,
        String kycStatus
) {
}
```

- [ ] **Step 4: Update `UserGetInfoService`**

```java
        return UserProfileInfo.builder()
                .fullName(appUser.getFullName())
                .username(appUser.getUsername().getValue())
                .email(appUser.getEmail().getValue())
                .profileImageUrl(appUser.getProfileImage().getUrl())
                .kycStatus(appUser.getKycStatus().name())
                .build();
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :modules:auth-service:test --tests "vn.sunvibe.auth.core.application.usecase.user.impl.UserGetInfoServiceTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/auth-service/src/main/java/vn/sunvibe/auth/core/presentation/dto/response/user/UserProfileInfo.java \
        modules/auth-service/src/main/java/vn/sunvibe/auth/core/application/usecase/user/impl/UserGetInfoService.java \
        modules/auth-service/src/test/java/vn/sunvibe/auth/core/application/usecase/user/impl/UserGetInfoServiceTest.java
git commit -m "Expose kycStatus on the user profile endpoint"
```

---

## Final Verification

- [ ] Run the full build: `./gradlew build`. Expected: all modules compile, every test from Tasks 1–8 passes, no MapStruct unmapped-property errors.
- [ ] Run `/code-review` on the full diff (per the project owner's explicit request) before considering this change complete.
- [ ] Manually confirm against a real (or freshly created) dev Postgres database: boot the app once, verify Flyway creates `roles`/`users`/`user_roles` with the exact column set from Task 4's migrations, then register a user via `POST /api/users/command/register` with an `idCardNumber` and confirm (a) the row's `id_card_number` column is ciphertext, not plaintext, (b) registering the same `idCardNumber` again is rejected, (c) deactivating a user frees their username/email for a new registration.
