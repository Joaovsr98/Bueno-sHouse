package com.restaurante.sistema.modules.inventory.repository;

import com.restaurante.sistema.modules.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findByUnitId(Long unitId);

    @Query("SELECT i FROM InventoryItem i WHERE i.unitId = :unitId AND i.currentQuantity < i.minimumQuantity")
    List<InventoryItem> findBelowMinimum(@Param("unitId") Long unitId);
}
