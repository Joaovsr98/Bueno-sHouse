package com.restaurante.sistema.modules.ordering.dto;

import java.math.BigDecimal;

public record OrderItemAdditionalResponse(
        Long id,
        String name,
        BigDecimal price,
        int quantity
) {}
