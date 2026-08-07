package com.restaurante.sistema.modules.payments.dto;

import java.math.BigDecimal;

public record OrderBalanceResponse(
        Long orderId,
        BigDecimal total,
        BigDecimal totalPaid,
        BigDecimal remaining,
        boolean fullyPaid
) {}
