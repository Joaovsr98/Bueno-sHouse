package com.restaurante.sistema.modules.kitchen.dto;

import java.time.Instant;

public record KitchenTaskResponse(
        Long orderItemId,
        Long orderId,
        Long orderNumber,
        String productName,
        int quantity,
        String notes,
        String status,
        Instant startedAt,
        Instant completedAt,
        Instant orderCreatedAt
) {}
