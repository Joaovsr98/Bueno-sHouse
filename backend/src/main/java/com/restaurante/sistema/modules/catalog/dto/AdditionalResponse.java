package com.restaurante.sistema.modules.catalog.dto;

import java.math.BigDecimal;

public record AdditionalResponse(
        Long id,
        String name,
        BigDecimal price,
        boolean active
) {}
