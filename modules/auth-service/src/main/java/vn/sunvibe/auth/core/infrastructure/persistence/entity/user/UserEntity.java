package vn.sunvibe.auth.core.infrastructure.persistence.entity.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.sunvibe.auth.core.domain.model.user.enums.KycStatus;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;
import vn.sunvibe.auth.core.infrastructure.persistence.converter.manual.EncryptedStringAttributeConverter;

import java.time.Instant;
import java.util.Set;

/*
 * Uniqueness of username, email and id_card_number_hash is enforced by
 * partial unique indexes in the Flyway migration, scoped to non-deleted
 * rows (WHERE deleted_at IS NULL) so a soft-deleted user's identifiers can
 * be reused. It must NOT be declared here (unique = true / unique @Index):
 * ddl-auto would turn that into a full, all-rows constraint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "USERS")
public class UserEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    /**
     * Full name
     */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /**
     * Username (VO → String)
     */
    @Column(nullable = false, length = 32)
    private String username;

    /**
     * Email (VO → String, lower-case). Stays nullable — a user may not
     * have one set yet.
     */
    @Column(name = "email", length = 100)
    private String email; // nullable – user may not be update yet

    /**
     * Hash of password(Password VO → String)
     */
    @Column(name = "password", nullable = false) // default length - 255
    private String password;

    /**
     * Profile image (ProfileImage VO → String URL)
     */
    @Column(name = "profile_image", length = 500)
    private String profileImage; // nullable – user may not be updated yet

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
    @Column(name = "id_card_number_hash", nullable = false, length = 64)
    private String idCardNumberHash;

    @Column(name = "id_card_front_url", columnDefinition = "TEXT")
    private String idCardFrontUrl;

    @Column(name = "id_card_back_url", columnDefinition = "TEXT")
    private String idCardBackUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    /*─────────────── AUDIT COLUMNS ───────────────*/
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /*─────────────── COLLECTION & ENUM ───────────────*/

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(
            name = "USER_ROLES",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id")
    )
    private Set<UserRoleEntity> userRoles;

    /**
     * Account Lifecycle Status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 20)
    private UserStatus userStatus;

}

