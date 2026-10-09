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

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles " +
            "WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<UserEntity> findActiveById(@Param("id") String id);

}
