package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InventoryItemRequest(
        @NotNull Long unitId,
        @NotBlank String name,
        @NotBlank String unitOfMeasure,
        BigDecimal minimumQuantity,
        BigDecimal costPerUnit
) {}
