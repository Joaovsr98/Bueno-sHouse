package com.restaurante.sistema.modules.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecipeResponse(
        Long id,
        Long productId,
        List<Item> items
) {
    public record Item(Long inventoryItemId, String inventoryItemName, BigDecimal quantity, String unitOfMeasure) {}
}
