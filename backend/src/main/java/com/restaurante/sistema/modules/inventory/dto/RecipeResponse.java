package com.restaurante.sistema.modules.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecipeResponse(
        Long id,
        Long productId,
        BigDecimal yieldQuantity,
        List<Item> items,

        /** custo_prato = SUM(quantidade x fator_correcao x custo_unitario) / rendimento (RF-012). */
        BigDecimal costPerServing,

        /** food_cost% = custo_prato / preco_venda * 100 (RF-013). Null se o produto nao tiver preco. */
        BigDecimal foodCostPercent,

        /** GREEN (<=30%), YELLOW (31-35%) ou RED (>35%) - RN02: so avisa, nunca bloqueia. */
        String foodCostLevel
) {
    public record Item(
            Long inventoryItemId,
            String inventoryItemName,
            BigDecimal quantity,
            String unitOfMeasure,
            BigDecimal correctionFactor
    ) {}
}
