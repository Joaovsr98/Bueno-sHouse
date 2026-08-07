package com.restaurante.sistema.modules.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DeliveryResponse(
        Long id,
        Long orderId,
        Long courierId,
        String addressSnapshot,
        String neighborhoodSnapshot,
        BigDecimal fee,
        Integer estimatedMinutes,
        String status,
        String receivedByName,
        Instant assignedAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant leftAt,
        Instant deliveredAt
) {}
