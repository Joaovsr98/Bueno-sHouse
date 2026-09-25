package com.restaurante.sistema.modules.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PurchaseResponse(
        Long id,
        Long unitId,
        Long supplierId,
        String supplierName,
        String status,
        BigDecimal total,
        LocalDate purchasedAt,
        List<Item> items
) {
    public record Item(
            Long inventoryItemId,
            String inventoryItemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}
}
