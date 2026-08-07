package com.restaurante.sistema.modules.catalog.dto;

import java.math.BigDecimal;

public record ProductVariationResponse(
        Long id,
        String name,
        BigDecimal priceDelta,
        boolean active
) {}
