package vn.sunvibe.property.core.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.sunvibe.property.core.infrastructure.persistence.entity.WardEntity;

import java.util.List;

@Repository
public interface WardJpaRepository extends JpaRepository<WardEntity, String> {

    List<WardEntity> findByProvinceCode(String provinceCode);
}
