package com.restaurante.sistema.modules.catalog.repository;

import com.restaurante.sistema.modules.catalog.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUnitIdAndDeletedAtIsNullOrderByDisplayOrder(Long unitId);
}
