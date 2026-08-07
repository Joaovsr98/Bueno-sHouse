package com.restaurante.sistema.modules.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        Long orderId,
        String method,
        BigDecimal amount,
        String status,
        Instant createdAt
) {}
