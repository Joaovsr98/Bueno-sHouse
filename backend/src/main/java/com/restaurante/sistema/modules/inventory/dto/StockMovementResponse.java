package com.restaurante.sistema.modules.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMovementResponse(
        Long id,
        String type,
        BigDecimal quantity,
        String reason,
        Instant createdAt
) {}
