package com.restaurante.sistema.modules.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdditionalGroupRequest(
        @NotNull Long unitId,
        @NotBlank String name,
        @Min(0) Integer minQuantity,
        @Min(1) Integer maxQuantity,
        Boolean required
) {}
