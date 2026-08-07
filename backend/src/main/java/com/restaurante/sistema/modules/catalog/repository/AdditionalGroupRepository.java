package com.restaurante.sistema.modules.catalog.repository;

import com.restaurante.sistema.modules.catalog.domain.AdditionalGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdditionalGroupRepository extends JpaRepository<AdditionalGroup, Long> {
    List<AdditionalGroup> findByUnitId(Long unitId);
}
