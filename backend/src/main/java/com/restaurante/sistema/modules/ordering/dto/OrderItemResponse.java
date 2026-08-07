package com.restaurante.sistema.modules.ordering.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal,
        String notes,
        String status,
        Long kitchenSectorId,
        Instant startedAt,
        Instant completedAt,
        List<OrderItemAdditionalResponse> additionals
) {}
