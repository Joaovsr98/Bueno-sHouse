package com.restaurante.sistema.modules.catalog.repository;

import com.restaurante.sistema.modules.catalog.domain.ProductVariation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariationRepository extends JpaRepository<ProductVariation, Long> {
}
