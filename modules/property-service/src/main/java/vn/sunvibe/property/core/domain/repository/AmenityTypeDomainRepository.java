package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.amenity.AmenityType;

import java.util.List;
import java.util.Optional;

public interface AmenityTypeDomainRepository {

    Optional<AmenityType> findById(Integer id);

    List<AmenityType> findAll();
}