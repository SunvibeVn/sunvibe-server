package vn.sunvibe.property.core.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.sunvibe.property.core.domain.model.area.Province;
import vn.sunvibe.property.core.domain.model.area.Ward;
import vn.sunvibe.property.core.domain.repository.AdministrativeAreaDomainRepository;
import vn.sunvibe.property.core.infrastructure.persistence.mapper.manual.AdministrativeAreaEntityMapper;
import vn.sunvibe.property.core.infrastructure.persistence.repository.ProvinceJpaRepository;
import vn.sunvibe.property.core.infrastructure.persistence.repository.WardJpaRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdministrativeAreaRepositoryAdapter implements AdministrativeAreaDomainRepository {

    private final ProvinceJpaRepository provinceJpaRepository;
    private final WardJpaRepository wardJpaRepository;
    private final AdministrativeAreaEntityMapper mapper;

    @Override
    public List<Province> findAllProvinces() {
        return provinceJpaRepository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Province> findProvince(String code) {
        return provinceJpaRepository.findById(code).map(mapper::toDomain);
    }

    @Override
    public List<Ward> findWardsByProvince(String provinceCode) {
        return wardJpaRepository.findByProvinceCode(provinceCode).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Ward> findWard(String code) {
        return wardJpaRepository.findById(code).map(mapper::toDomain);
    }
}
