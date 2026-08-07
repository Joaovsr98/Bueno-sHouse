package com.restaurante.sistema.modules.inventory.dto;

import java.math.BigDecimal;

public record InventoryItemResponse(
        Long id,
        Long unitId,
        String name,
        String unitOfMeasure,
        BigDecimal currentQuantity,
        BigDecimal minimumQuantity,
        BigDecimal costPerUnit,
        boolean belowMinimum
) {}
