package vn.sunvibe.auth.core.infrastructure.persistence.jpa.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.auth.core.infrastructure.persistence.jpa.entity.user.UserRoleEntity;

import java.util.*;

@Repository
public interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, UUID> {

    Optional<UserRoleEntity> findByName(String name);

    Set<UserRoleEntity> findByNameIn(Collection<String> names);

//    List<UserRoleEntity> findAllByUsername(Collection<String> names);

}
