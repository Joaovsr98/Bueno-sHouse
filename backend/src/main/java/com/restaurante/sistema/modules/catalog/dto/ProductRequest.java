package com.restaurante.sistema.modules.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record ProductRequest(
        @NotNull(message = "A unidade e obrigatoria")
        Long unitId,

        @NotNull(message = "A categoria e obrigatoria")
        Long categoryId,

        @NotBlank(message = "O nome do produto e obrigatorio")
        String name,

        String description,

        @NotNull(message = "O preco base e obrigatorio")
        @DecimalMin(value = "0.0", message = "O preco nao pode ser negativo")
        BigDecimal basePrice,

        String imageUrl,
        Integer prepTimeMinutes,
        Long kitchenSectorId,
        Boolean available,
        Boolean featured,

        @Valid
        List<ProductVariationRequest> variations,

        Set<Long> additionalGroupIds
) {}
