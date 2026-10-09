package vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.user;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import vn.sunvibe.auth.core.domain.model.user.enums.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "USERS",
        indexes = {
                @Index(name = "idx_users_username", columnList = "username", unique = true),
                @Index(name = "idx_users_email", columnList = "email", unique = true)
        })
public class UserEntity {

    @Id
    @Column(name = "id")
    private String id;

    /**
     * Full name
     */
    @Column(name = "full_name")
    private String fullName;

    /**
     * Username (VO → String)
     */
    @Column(nullable = false, length = 32, unique = true)
    private String username;

    /**
     * Email (VO → String, lower-case)
     */
    @Column(name = "email", length = 100, unique = true)
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

    /*─────────────── AUDIT COLUMNS ───────────────*/
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;

    @Column(name = "last_login")
    private Instant lastLogin;

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

