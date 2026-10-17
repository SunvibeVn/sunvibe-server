package vn.sunvibe.auth.core.infrastructure.persistence.entity.user;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.PermissionEntity;

import java.util.Set;
import java.util.UUID;

/**
 * Table {@code ROLES}: one row per {@code RoleName}, inserted by the Flyway migration
 * {@code V202610160900__seed_roles.sql}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ROLES", uniqueConstraints = @UniqueConstraint(name = "uk_roles_name", columnNames = "name"))
public class UserRoleEntity {
    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(nullable = false)
    private String name; // eg: ADMIN, USER, MODERATOR

    @Column(length = 500)
    private String description;

    @ManyToMany(mappedBy = "userRoles")
    private Set<UserEntity> users;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ROLE_PERMISSION", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<PermissionEntity> permissions;

}
