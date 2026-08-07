package com.restaurante.sistema.modules.catalog.repository;

import com.restaurante.sistema.modules.catalog.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByUnitIdAndDeletedAtIsNull(Long unitId);
    List<Product> findByUnitIdAndCategoryIdAndDeletedAtIsNull(Long unitId, Long categoryId);
}
