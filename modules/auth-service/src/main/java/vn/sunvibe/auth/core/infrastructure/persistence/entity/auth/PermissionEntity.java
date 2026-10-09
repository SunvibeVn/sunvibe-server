package vn.sunvibe.auth.core.infrastructure.persistence.entity.auth;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import vn.sunvibe.auth.core.infrastructure.persistence.entity.user.UserRoleEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "PERMISSION")
public class PermissionEntity {

    @Id
    @UuidGenerator
    @Column(name="id")
    private UUID id;

    @Column(unique = true, nullable = false)
    private String name; // eg: CAN_VIEW_USER, CAN_EDIT_USER

    @Column(length = 255)
    private String description;

    @ManyToMany(mappedBy = "permissions")
    private Set<UserRoleEntity> roles = new HashSet<>();

}
