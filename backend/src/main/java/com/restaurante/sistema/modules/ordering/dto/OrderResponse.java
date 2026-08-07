package com.restaurante.sistema.modules.ordering.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String publicId,
        Long orderNumber,
        Long unitId,
        String channel,
        Long commandId,
        String status,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal serviceFee,
        BigDecimal deliveryFee,
        BigDecimal total,
        String notes,
        Instant createdAt,
        Instant completedAt,
        List<OrderItemResponse> items
) {}
