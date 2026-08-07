package com.restaurante.sistema.modules.inventory.repository;

import com.restaurante.sistema.modules.inventory.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findByUnitId(Long unitId);
}
