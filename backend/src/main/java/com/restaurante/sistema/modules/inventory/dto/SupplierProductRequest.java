package com.restaurante.sistema.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SupplierProductRequest(
        @NotNull Long inventoryItemId,
        @NotNull @Positive BigDecimal price,
        @NotBlank String unitOfMeasure
) {}
