package vn.sunvibe.property.core.domain.repository;

import vn.sunvibe.property.core.domain.model.rule.RuleCategory;

import java.util.List;
import java.util.Optional;

public interface RuleCategoryDomainRepository {

    Optional<RuleCategory> findById(Integer id);

    List<RuleCategory> findAll();
}