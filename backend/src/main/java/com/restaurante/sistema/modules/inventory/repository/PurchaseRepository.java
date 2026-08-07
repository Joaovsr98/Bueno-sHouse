package com.restaurante.sistema.modules.inventory.repository;

import com.restaurante.sistema.modules.inventory.domain.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findByUnitId(Long unitId);
}
