package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecipeItemRequest(
        @NotNull Long inventoryItemId,
        @NotNull @Positive BigDecimal quantity
) {}
