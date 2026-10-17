package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.area.Province;
import vn.sunvibe.property.core.domain.model.area.Ward;

import java.util.List;
import java.util.Optional;

/** Read-only access to the administrative-area catalog; rows are added by migrations, never by the application. */
public interface AdministrativeAreaDomainRepository {

    List<Province> findAllProvinces();

    Optional<Province> findProvince(String code);

    List<Ward> findWardsByProvince(String provinceCode);

    Optional<Ward> findWard(String code);
}
