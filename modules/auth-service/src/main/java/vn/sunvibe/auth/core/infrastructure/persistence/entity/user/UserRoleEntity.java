package vn.sunvibe.auth.core.infrastructure.persistence.entity.user;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.auth.PermissionEntity;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ROLE")
public class UserRoleEntity {
    @Id
    @UuidGenerator
    @Column(name = "id")
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name; // eg: ADMIN, USER, MODERATOR

    @Column(length = 500)
    private String description;

    @ManyToMany(mappedBy = "userRoles")
    private Set<UserEntity> users;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ROLE_PERMISSION", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<PermissionEntity> permissions;

}
