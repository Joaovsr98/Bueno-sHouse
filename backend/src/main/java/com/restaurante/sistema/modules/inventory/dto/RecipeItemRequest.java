package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecipeItemRequest(
        @NotNull Long inventoryItemId,
        @NotNull @Positive BigDecimal quantity,

        /** RN08: fator de correcao sempre >= 1.0 (perda por limpeza/preparo). */
        @NotNull @DecimalMin(value = "1.0", message = "O fator de correcao precisa ser >= 1.0")
        BigDecimal correctionFactor
) {}
