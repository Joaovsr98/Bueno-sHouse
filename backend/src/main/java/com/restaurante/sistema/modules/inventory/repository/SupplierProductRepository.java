package com.restaurante.sistema.modules.inventory.repository;

import com.restaurante.sistema.modules.inventory.domain.SupplierProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, Long> {
    List<SupplierProduct> findByInventoryItemIdOrderByPriceAsc(Long inventoryItemId);
    List<SupplierProduct> findBySupplierId(Long supplierId);
    Optional<SupplierProduct> findBySupplierIdAndInventoryItemId(Long supplierId, Long inventoryItemId);
}
