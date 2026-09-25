package com.restaurante.sistema.modules.inventory.dto;

import java.math.BigDecimal;

public record SupplierProductResponse(
        Long id,
        Long supplierId,
        Long inventoryItemId,
        String inventoryItemName,
        BigDecimal price,
        String unitOfMeasure
) {}
