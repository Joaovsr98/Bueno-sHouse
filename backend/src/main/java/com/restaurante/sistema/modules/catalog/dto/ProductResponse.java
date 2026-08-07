package com.restaurante.sistema.modules.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record ProductResponse(
        Long id,
        Long unitId,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        BigDecimal basePrice,
        String imageUrl,
        Integer prepTimeMinutes,
        Long kitchenSectorId,
        boolean available,
        boolean featured,
        List<ProductVariationResponse> variations,
        Set<Long> additionalGroupIds
) {}
