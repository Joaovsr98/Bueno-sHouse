package com.restaurante.sistema.modules.inventory.repository;

import com.restaurante.sistema.modules.inventory.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByInventoryItemIdOrderByCreatedAtDesc(Long inventoryItemId);
    boolean existsByReferenceOrderItemIdAndType(Long referenceOrderItemId, String type);
}
