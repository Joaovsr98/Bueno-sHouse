package com.restaurante.sistema.modules.cashregister.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CashMovementResponse(
        Long id,
        String type,
        BigDecimal amount,
        String reason,
        Instant createdAt
) {}
